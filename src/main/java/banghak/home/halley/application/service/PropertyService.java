package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateDraftRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.application.event.PropertyInsightChanged;
import banghak.home.halley.application.event.PropertyDeletedEvent;
import banghak.home.halley.config.exception.AdminCannotOwnPropertyException;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NoGroupException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.EditVersionStore;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.ConcurrentEditException;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final UserRepository userRepository;
    private final AgentService agentService;
    private final EditVersionStore editVersionStore;
    private final GeoService geoService;
    private final ApplicationEventPublisher eventPublisher;

    public PropertyService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                           UserRepository userRepository,
                           AgentService agentService,
                           EditVersionStore editVersionStore,
                           GeoService geoService,
                           ApplicationEventPublisher eventPublisher) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.agentService = agentService;
        this.editVersionStore = editVersionStore;
        this.geoService = geoService;
        this.eventPublisher = eventPublisher;
    }

    public List<PropertyResponse> list() {
        // admin은 전부, 회원은 자기 그룹만 (설계 I87)
        final List<Property> properties = propertyAccessGuard.isAdmin()
                ? propertyRepository.findAll()
                : propertyAccessGuard.currentGroupId()
                        .map(propertyRepository::findByGroupId)
                        .orElseGet(List::of);
        return properties.stream().map(this::toResponse).toList();
    }

    public PropertyResponse get(Long id) {
        return toResponse(propertyAccessGuard.require(id));
    }

    @Transactional

    /**
     * 등록자의 그룹 (설계 I87). 매물은 <b>반드시</b> 그룹에 딸립니다.
     *
     * <p>admin은 어느 그룹에도 속하지 않으므로 등록할 수 없습니다 — 등록하면 아무도 볼 수 없고
     * 그룹이 사라져도 남는 매물이 생깁니다.
     */
    private Long requireOwnerGroupId() {
        if (propertyAccessGuard.isAdmin()) {
            throw new AdminCannotOwnPropertyException();
        }
        return propertyAccessGuard.currentGroupId().orElseThrow(NoGroupException::new);
    }

    /** 등록자 닉네임을 매물에 복사해 둔다 — 탈퇴해도 화면에 남아야 한다 (설계 I88). */
    private String currentNickname() {
        return propertyAccessGuard.currentUser().map(u -> u.nickname()).orElse(null);
    }

    public PropertyResponse create(PropertyRequest request) {
        validate(request);
        final Long groupId = requireOwnerGroupId();
        final String nickname = currentNickname();
        final Coordinates coords = resolveCoordinates(request);
        final boolean fromPaste = request.rawPasteText() != null && !request.rawPasteText().isBlank();
        final Property saved = propertyRepository.save(new Property(
                null,
                request.name(),
                request.dongHo(),
                request.dealType(),
                request.priceDeposit(),
                request.maintenanceFee(),
                request.addressRoad(),
                request.addressJibun(),
                coords.lat(),
                coords.lng(),
                request.areaSupplyM2(),
                request.areaExclusiveM2(),
                request.floorRaw(),
                request.floorNo(),
                request.floorTotal(),
                request.floorBand(),
                request.roomBath(),
                request.direction(),
                request.approvalYear(),
                request.moveInType(),
                request.moveInDate(),
                request.parkingPerHousehold(),
                request.totalHouseholds(),
                request.heatingType(),
                request.buildingCount(),
                request.kbPrice(),
                request.brokerageFee(),
                request.brokerageRate(),
                request.acquisitionTax(),
                request.propertyTax(),
                request.comprehensiveTax(),
                request.schoolName(),
                request.schoolWalkMinutes(),
                request.schoolName() == null || request.schoolName().isBlank() ? null : SchoolSource.PASTE,
                null, null, null,
                fromPaste ? SourceType.PASTE : SourceType.MANUAL,
                listingUrl(request.sourceUrl()),
                request.naverArticleNo(),
                request.rawPasteText(),
                fromPaste ? "parser-v1" : null,
                null,
                false,
                ListingStatus.ACTIVE,
                true,
                null, 0, null,
                groupId, nickname,
                currentUserId(),
                Instant.now()));
        agentService.upsertFromPaste(saved.id(), request.agent());
        eventPublisher.publishEvent(new PropertyCreatedEvent(saved.id()));
        editVersionStore.bump(versionKey(saved.id()));
        return toResponse(saved);
    }

    @Transactional
    public PropertyResponse createDraft(CreateDraftRequest request) {
        if (request.sourceUrl() == null || request.sourceUrl().isBlank()) {
            throw new InvalidPropertyRequestException("원본 URL은 필수입니다");
        }
        final String name = request.memo() == null || request.memo().isBlank()
                ? "작성 중" : request.memo().trim();
        final Property saved = propertyRepository.save(new Property(
                null, name, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null,
                SourceType.PASTE, listingUrl(request.sourceUrl()), null, null, null, null,
                true, ListingStatus.ACTIVE, true,
                null, 0, null,
                requireOwnerGroupId(), currentNickname(), currentUserId(), Instant.now()));
        return toResponse(saved);
    }

    public PropertyResponse update(Long id, PropertyRequest request, Long editVersion) {
        validate(request);
        final Property existing = propertyAccessGuard.require(id);
        checkEditVersion(id, editVersion);
        final Coordinates coords = resolveCoordinates(request);
        final Property updated = propertyRepository.update(new Property(
                existing.id(),
                request.name(),
                request.dongHo(),
                request.dealType(),
                request.priceDeposit(),
                request.maintenanceFee(),
                request.addressRoad(),
                request.addressJibun(),
                coords.lat(),
                coords.lng(),
                request.areaSupplyM2(),
                request.areaExclusiveM2(),
                request.floorRaw(),
                request.floorNo(),
                request.floorTotal(),
                request.floorBand(),
                request.roomBath(),
                request.direction(),
                request.approvalYear(),
                request.moveInType(),
                request.moveInDate(),
                request.parkingPerHousehold(),
                request.totalHouseholds(),
                request.heatingType(),
                request.buildingCount(),
                request.kbPrice(),
                request.brokerageFee(),
                request.brokerageRate(),
                request.acquisitionTax(),
                request.propertyTax(),
                request.comprehensiveTax(),
                request.schoolName(),
                request.schoolWalkMinutes(),
                request.schoolName() == null || request.schoolName().isBlank() ? null
                        : java.util.Objects.equals(request.schoolName(), existing.schoolName())
                        ? existing.schoolSource() : SchoolSource.PASTE,
                existing.pnu(),
                existing.officialPrice(),
                existing.officialPriceYear(),
                existing.sourceType(),
                listingUrl(request.sourceUrl()),
                existing.naverArticleNo(),
                existing.rawPasteText(),
                existing.parserVersion(),
                existing.parseConfidence(),
                false,
                existing.listingStatus(),
                existing.active(),
                existing.lastCheckedAt(),
                existing.checkFailStreak(),
                existing.soldDetectedAt(),
                existing.groupId(), existing.createdByNickname(),
                existing.createdBy(),
                existing.createdAt()));
        agentService.upsertFromPaste(id, request.agent());
        editVersionStore.bump(versionKey(id));
        // 바뀐 게 있으면 AI에게 다시 묻는다 (설계 I113). 면적·층·가격·주차가 그대로
        // 프롬프트에 실리므로, 고쳐 놓고 옛 판단을 그대로 두면 안 된다.
        //
        // 무엇이 바뀌었는지 항목을 손으로 나열하지 않는다 — 그 목록은 필드가 늘 때마다
        // 조용히 낡는다. 레코드끼리 통째로 비교하면 새 필드도 저절로 걸린다.
        // 실제로 다시 물을지는 프롬프트 해시가 가린다(I59) — 프롬프트에 안 실리는 칸만
        // 바뀌었으면 해시가 같아 호출 없이 끝난다
        if (!existing.equals(updated)) {
            eventPublisher.publishEvent(PropertyInsightChanged.edited(id, currentNickname()));
        }
        return toResponse(updated);
    }

    public void delete(Long id) {
        final Property existing = propertyAccessGuard.require(id);
        propertyRepository.delete(id);
        // 지우고 나면 이름도 그룹도 알 수 없어 미리 담아 보낸다 (설계 I96)
        eventPublisher.publishEvent(
                new PropertyDeletedEvent(existing.groupId(), existing.name()));
    }

    public PropertyResponse updateStatus(Long id, ListingStatus listingStatus) {
        if (listingStatus == null) {
            throw new InvalidPropertyRequestException("판매 상태는 필수입니다");
        }
        final Property existing = propertyAccessGuard.require(id);
        final boolean active = listingStatus != ListingStatus.SOLD_OUT && listingStatus != ListingStatus.ARCHIVED;
        propertyRepository.updateListingStatus(
                id,
                listingStatus,
                active,
                listingStatus == ListingStatus.ACTIVE ? 0 : existing.checkFailStreak(),
                listingStatus == ListingStatus.SOLD_OUT ? Instant.now() : null);
        return get(id);
    }

    public List<PropertyResponse> recentSoldOut() {
        return propertyRepository.findRecentSoldOut(10).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 원본 URL은 화면에서 링크로 열리는 값이라
     * <b>http/https만</b> 받는다. `javascript:` 같은 스킴이 들어오면 링크를 누르는 순간
     * 스크립트가 도는 통로가 된다 (설계 I62).
     */
    private String listingUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.trim();
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new InvalidPropertyRequestException("원본 URL은 http:// 또는 https:// 로 시작해야 합니다");
        }
        return trimmed;
    }

    private void validate(PropertyRequest request) {
        if (request.dealType() == null) {
            throw new InvalidPropertyRequestException("거래유형은 필수입니다");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidPropertyRequestException("매물명은 필수입니다");
        }
    }

    /**
     * 좌표가 요청에 명시돼 있으면 그대로 쓰고, 없으면 주소(도로명 우선)로 지오코딩해 채운다.
     */
    private Coordinates resolveCoordinates(PropertyRequest request) {
        if (request.lat() != null && request.lng() != null) {
            return new Coordinates(request.lat(), request.lng());
        }
        final String address = firstNonBlank(request.addressRoad(), request.addressJibun());
        if (address == null) {
            return new Coordinates(null, null);
        }
        final Optional<GeoSearchResult> geo = geoService.geocode(address);
        if (geo.isEmpty()) {
            log.warn("Geocoding failed - saving property without coordinates. address={}", address);
            return new Coordinates(null, null);
        }
        return new Coordinates(geo.get().lat(), geo.get().lng());
    }

    private static String firstNonBlank(String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record Coordinates(BigDecimal lat, BigDecimal lng) {
    }

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }

    private void checkEditVersion(Long id, Long sentVersion) {
        if (sentVersion == null) {
            return;
        }
        final long current = editVersionStore.current(versionKey(id));
        if (sentVersion != current) {
            throw new ConcurrentEditException();
        }
    }

    private String versionKey(Long id) {
        return "property:" + id;
    }

    private PropertyResponse toResponse(Property p) {
        return PropertyResponse.from(p, nicknameOf(p.createdBy()), editVersionStore.current(versionKey(p.id())));
    }

    /** 매물 카드에 등록자를 보여주기 위한 닉네임 (설계 I53). 삭제된 사용자면 null. */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::nickname).orElse(null);
    }
}
