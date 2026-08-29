package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateRegulationProfileRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaRequest;
import banghak.home.halley.adapter.inbound.web.dto.RegulatedAreaResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulationParamResponse;
import banghak.home.halley.adapter.inbound.web.dto.RegulationProfileResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateRegulationParamRequest;
import banghak.home.halley.adapter.outbound.persistence.RegulatedAreaRepository;
import banghak.home.halley.adapter.outbound.persistence.RegulationParamRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.InvalidRegulationException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationParam;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.setting.ConfigCategory;
import banghak.home.halley.domain.setting.ConfigValueType;
import banghak.home.halley.domain.setting.SystemConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 규제 파라미터·규제지역 관리 (설계 I68).
 *
 * <p>규제 수치에 공개 API가 없어 사람이 관리합니다(설계 I64). 그렇다면 <b>DB에 직접 손대지 않고</b>
 * 고칠 수 있어야 하고, <b>왜 그 값이 됐는지</b>가 남아야 합니다.
 *
 * <p>규제가 바뀌면 값을 덮어쓰는 대신 <b>새 프로파일을 복제해</b> 만들고 활성만 전환합니다.
 * 옛 프로파일이 남아야 과거 산출값을 재현할 수 있습니다.
 */
@Slf4j
@Service
public class RegulationAdminService {

    private static final String PROFILE_KEY = "loan.regulation.profile";
    private static final String DEFAULT_PROFILE = "2025-10-15";

    private final RegulationParamRepository regulationParamRepository;
    private final RegulatedAreaRepository regulatedAreaRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ScoringService scoringService;

    public RegulationAdminService(RegulationParamRepository regulationParamRepository,
                                  RegulatedAreaRepository regulatedAreaRepository,
                                  SystemConfigRepository systemConfigRepository,
                                  ScoringService scoringService) {
        this.regulationParamRepository = regulationParamRepository;
        this.regulatedAreaRepository = regulatedAreaRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.scoringService = scoringService;
    }

    public RegulationProfileResponse profiles() {
        final String active = activeProfile();
        return new RegulationProfileResponse(
                active,
                regulationParamRepository.findProfiles(),
                regulationParamRepository.findByProfile(active).stream()
                        .sorted(Comparator.comparing(RegulationParam::paramKey))
                        .map(RegulationParamResponse::from)
                        .toList());
    }

    /**
     * 값을 고친다. <b>LTV·DSR은 가격 채점(PRICE)의 입력이므로</b> 바뀌면 전 매물을 다시 채점한다
     * (설계 5.2.1 — 예산 상한 = 현금 + 대출 한도).
     */
    @Transactional
    public RegulationProfileResponse updateParams(List<UpdateRegulationParamRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return profiles();
        }
        boolean changed = false;
        for (final UpdateRegulationParamRequest request : requests) {
            final RegulationParam existing = regulationParamRepository.findById(request.id())
                    .orElseThrow(NotFoundListingsException::new);
            final String value = validated(existing, request.paramValue());
            if (value.equals(existing.paramValue())) {
                continue;
            }
            regulationParamRepository.update(new RegulationParam(
                    existing.id(), existing.profile(), existing.paramKey(), value,
                    existing.valueType(), existing.description(), currentAdminId(), Instant.now()));
            log.info("Regulation parameter updated. profile={}, key={}, {} -> {}",
                    existing.profile(), existing.paramKey(), existing.paramValue(), value);
            changed = true;
        }
        if (changed) {
            scoringService.rescoreAll();
        }
        return profiles();
    }

    /**
     * 새 프로파일을 만든다. 규제가 바뀌면 <b>덮어쓰지 않고</b> 복제해 새로 만들어야
     * 과거 산출값을 재현할 수 있다.
     */
    @Transactional
    public RegulationProfileResponse createProfile(CreateRegulationProfileRequest request) {
        final String profile = request == null || request.profile() == null ? "" : request.profile().trim();
        if (profile.isEmpty()) {
            throw new InvalidRegulationException("프로파일 이름은 필수입니다");
        }
        if (regulationParamRepository.findProfiles().contains(profile)) {
            throw new InvalidRegulationException("이미 있는 프로파일입니다: " + profile);
        }
        final String source = request.copyFrom() == null || request.copyFrom().isBlank()
                ? activeProfile() : request.copyFrom().trim();
        final int copied = regulationParamRepository.copyProfile(source, profile, currentAdminId());
        if (copied == 0) {
            throw new InvalidRegulationException("복제할 프로파일에 값이 없습니다: " + source);
        }
        log.info("Regulation profile created. profile={}, copiedFrom={}, params={}", profile, source, copied);
        if (Boolean.TRUE.equals(request.activate())) {
            activateProfile(profile);
        }
        return profiles();
    }

    @Transactional
    public RegulationProfileResponse activateProfile(String profile) {
        if (!regulationParamRepository.findProfiles().contains(profile)) {
            throw new InvalidRegulationException("없는 프로파일입니다: " + profile);
        }
        systemConfigRepository.findById(PROFILE_KEY).ifPresentOrElse(
                existing -> systemConfigRepository.update(new SystemConfig(
                        existing.configKey(), profile, existing.valueType(), existing.category(),
                        existing.description(), existing.masked(), currentAdminId(), existing.updatedAt())),
                () -> systemConfigRepository.save(new SystemConfig(
                        PROFILE_KEY, profile, ConfigValueType.STRING, ConfigCategory.LOAN,
                        "대출 계산에 쓰는 규제 프로파일", false, currentAdminId(), Instant.now())));
        log.info("Active regulation profile switched. profile={}", profile);
        // 프로파일이 바뀌면 LTV·DSR이 통째로 달라진다
        scoringService.rescoreAll();
        return profiles();
    }

    // ── 규제지역 ──────────────────────────────────

    public List<RegulatedAreaResponse> areas() {
        final LocalDate today = LocalDate.now();
        return regulatedAreaRepository.findAll().stream()
                .map(area -> RegulatedAreaResponse.from(area, today))
                .toList();
    }

    @Transactional
    public List<RegulatedAreaResponse> addArea(RegulatedAreaRequest request) {
        regulatedAreaRepository.save(new RegulatedArea(
                null, validatedPrefix(request), validatedZone(request), trim(request.areaName()),
                request.designatedOn(), request.releasedOn(), trim(request.note()), Instant.now()));
        scoringService.rescoreAll();
        return areas();
    }

    @Transactional
    public List<RegulatedAreaResponse> deleteArea(Long id) {
        regulatedAreaRepository.findById(id).orElseThrow(NotFoundListingsException::new);
        regulatedAreaRepository.delete(id);
        scoringService.rescoreAll();
        return areas();
    }

    /** 법정동코드는 5자리(시군구) 또는 10자리(법정동)여야 한다. 그 사이 길이는 매칭되지 않는다. */
    private String validatedPrefix(RegulatedAreaRequest request) {
        final String prefix = trim(request.codePrefix());
        if (prefix == null || !prefix.matches("\\d{5}|\\d{10}")) {
            throw new InvalidRegulationException("법정동코드는 5자리(시군구) 또는 10자리(법정동)여야 합니다");
        }
        return prefix;
    }

    private RegulationZone validatedZone(RegulatedAreaRequest request) {
        if (request.zone() == null || request.zone() == RegulationZone.NORMAL) {
            throw new InvalidRegulationException("규제 구분은 조정대상지역 또는 투기과열지구여야 합니다");
        }
        return request.zone();
    }

    /** 값 형식을 지킨다. 숫자 칸에 글자가 들어가면 계산이 조용히 기본값으로 떨어진다. */
    private String validated(RegulationParam param, String raw) {
        final String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new InvalidRegulationException(param.paramKey() + " 값은 비울 수 없습니다");
        }
        try {
            switch (param.valueType()) {
                case INT -> Integer.parseInt(value);
                case DECIMAL -> new BigDecimal(value);
                default -> { }
            }
        } catch (NumberFormatException e) {
            throw new InvalidRegulationException(param.paramKey() + " 값이 숫자가 아닙니다: " + value);
        }
        return value;
    }

    private String activeProfile() {
        return systemConfigRepository.findById(PROFILE_KEY)
                .map(SystemConfig::configValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(DEFAULT_PROFILE);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long currentAdminId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }
}
