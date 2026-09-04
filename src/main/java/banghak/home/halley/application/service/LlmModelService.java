package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.UpdateLlmModelRequest;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.application.port.out.external.ClaudeModelsPort;
import banghak.home.halley.config.exception.InvalidLlmModelSettingException;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmModelOption;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 어느 자리에 어떤 모델을 쓸 것인가 (설계 I267). 고른 값은 {@code system_config} 에 둔다. */
@Slf4j
@Service
public class LlmModelService {

    private final SystemConfigRepository systemConfigRepository;
    private final ClaudeModelsPort claudeModelsPort;
    /** 아무것도 안 골랐을 때 쓰는 모델 — 배포로 정한다. */
    private final String defaultModel;

    public LlmModelService(SystemConfigRepository systemConfigRepository,
                           ClaudeModelsPort claudeModelsPort,
                           @Value("${llm.claude.model:claude-opus-5}") String defaultModel) {
        this.systemConfigRepository = systemConfigRepository;
        this.claudeModelsPort = claudeModelsPort;
        this.defaultModel = defaultModel;
    }

    /** 이 자리가 쓸 모델. 안 골랐으면 기본값 — 빈 문자열을 그대로 보내면 400이 난다. */
    public String modelFor(LlmFeature feature) {
        return systemConfigRepository.findById(feature.configKey())
                .map(SystemConfig::configValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(defaultModel);
    }

    /** 관리자 화면이 보여 줄 것 — 자리마다 지금 고른 값. */
    public Map<LlmFeature, String> current() {
        final Map<LlmFeature, String> chosen = new LinkedHashMap<>();
        for (final LlmFeature feature : LlmFeature.values()) {
            chosen.put(feature, modelFor(feature));
        }
        return chosen;
    }

    /** 관리자가 고른 것을 적는다. 아는 자리·목록 안 모델만 받는다 (설계 I267). */
    public void update(List<UpdateLlmModelRequest> requests) {
        final Set<String> allowed = available().stream()
                .map(LlmModelOption::id)
                .collect(Collectors.toSet());
        for (final UpdateLlmModelRequest request : requests) {
            final LlmFeature feature = LlmFeature.ofConfigKey(request.key())
                    .orElseThrow(() -> new InvalidLlmModelSettingException(
                            "AI 모델 설정이 아닙니다: " + request.key()));
            final String model = request.model() == null ? "" : request.model().trim();
            if (!model.isEmpty() && !allowed.contains(model)) {
                throw new InvalidLlmModelSettingException("쓸 수 없는 모델입니다: " + model);
            }
            final SystemConfig existing = systemConfigRepository.findById(feature.configKey())
                    .orElse(null);
            if (existing == null) {
                systemConfigRepository.save(new SystemConfig(
                        feature.configKey(), model, ConfigValueType.STRING, ConfigCategory.LLM,
                        feature.label() + " — " + feature.description(), false, null, null));
            } else {
                systemConfigRepository.update(new SystemConfig(
                        feature.configKey(), model, existing.valueType(), existing.category(),
                        existing.description(), existing.masked(), null, null));
            }
        }
    }

    /** 고를 수 있는 모델. Anthropic 이 안 알려 주면 지금 쓰는 것들만 돌려준다. */
    public List<LlmModelOption> available() {
        final List<LlmModelOption> models = claudeModelsPort.list();
        if (!models.isEmpty()) {
            return models;
        }
        log.info("Claude model list unavailable - offering the models already in use.");
        final List<LlmModelOption> fallback = new ArrayList<>();
        current().values().stream().distinct()
                .forEach(id -> fallback.add(LlmModelOption.of(id, id)));
        return List.copyOf(fallback);
    }
}
