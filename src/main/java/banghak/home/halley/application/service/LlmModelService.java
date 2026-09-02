package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.application.port.out.external.ClaudeModelsPort;
import banghak.home.halley.domain.llm.LlmFeature;
import banghak.home.halley.domain.llm.LlmModelOption;
import banghak.home.halley.adapter.inbound.web.dto.UpdateLlmModelRequest;
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

/**
 * 어느 자리에 어떤 모델을 쓸 것인가 (설계 I267).
 *
 * <p>지금까지는 환경변수 하나가 전부라 <b>바꾸려면 배포</b>해야 했습니다.
 * 이제 DB에 두고 관리자 화면에서 고릅니다.
 *
 * <h4>못 고르면 기본값</h4>
 *
 * <p>설정이 비어 있거나 없으면 {@code llm.claude.model} 을 씁니다.
 * <b>빈 문자열을 모델 이름으로 보내지 않습니다</b> — 400이 돌아오고, 그 실패는
 * "AI가 답을 안 줬다"로 조용히 묻힙니다.
 */
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

    /**
     * 이 자리가 쓸 모델.
     *
     * <p>부르는 쪽은 <b>늘 이 값을 그대로</b> 실으면 됩니다. 비어 있는 경우를
     * 저마다 다루면 어딘가에서 빈 문자열이 새어 나갑니다.
     */
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

    /**
     * 관리자가 고른 것을 적는다 (설계 I267).
     *
     * <p><b>아는 자리만 받습니다.</b> 임의의 설정 키를 이 길로 고칠 수 있으면
     * 규제 파라미터나 슬랙 스위치까지 바뀝니다.
     *
     * <p>모델 이름은 <b>Anthropic 이 준 목록 안</b>이어야 합니다. 손으로 아무 문자열이나
     * 넣으면 그 자리의 AI가 조용히 죽습니다 — 400은 "AI가 답을 안 줬다"로 묻힙니다.
     */
    public void update(List<UpdateLlmModelRequest> requests) {
        final java.util.Set<String> allowed = available().stream()
                .map(LlmModelOption::id)
                .collect(java.util.stream.Collectors.toSet());
        for (final UpdateLlmModelRequest request : requests) {
            final LlmFeature feature = LlmFeature.ofConfigKey(request.key())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "AI 모델 설정이 아닙니다: " + request.key()));
            final String model = request.model() == null ? "" : request.model().trim();
            if (!model.isEmpty() && !allowed.contains(model)) {
                throw new IllegalArgumentException("쓸 수 없는 모델입니다: " + model);
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

    /**
     * 고를 수 있는 모델.
     *
     * <p>Anthropic 이 안 알려 주면 <b>지금 쓰는 것들만</b> 돌려줍니다 —
     * 빈 드롭다운을 주면 고를 수도 없고, 지어낸 이름을 채우면 400이 납니다.
     */
    public List<LlmModelOption> available() {
        final List<LlmModelOption> models = claudeModelsPort.list();
        if (!models.isEmpty()) {
            return models;
        }
        log.info("Claude model list unavailable - offering the models already in use.");
        final List<LlmModelOption> fallback = new ArrayList<>();
        current().values().stream().distinct()
                .forEach(id -> fallback.add(new LlmModelOption(id, id)));
        return List.copyOf(fallback);
    }
}
