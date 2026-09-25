package banghak.home.halley.application.service;

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
import banghak.home.halley.domain.property.FloorValue;
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
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard propertyAccessGuard;
    private final UserRepository userRepository;
    private final AgentService agentService;
    private final ComplexService complexService;
    private final EditVersionStore editVersionStore;
    private final GeoService geoService;
    private final ApplicationEventPublisher eventPublisher;

    public PropertyService(PropertyAccessGuard propertyAccessGuard,
                                  PropertyRepository propertyRepository,
                           UserRepository userRepository,
                           AgentService agentService,
                           ComplexService complexService,
                           EditVersionStore editVersionStore,
                           GeoService geoService,
                           ApplicationEventPublisher eventPublisher) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.agentService = agentService;
        this.complexService = complexService;
        this.editVersionStore = editVersionStore;
        this.geoService = geoService;
        this.eventPublisher = eventPublisher;
    }

    public List<PropertyResponse> list() {
        // admin은 전부, 회원은 자기 그룹만
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

    private Long requireOwnerGroupId() {
        if (propertyAccessGuard.isAdmin()) {
            throw new AdminCannotOwnPropertyException();
        }
        return propertyAccessGuard.currentGroupId().orElseThrow(NoGroupException::new);
    }

    /** 등록자 닉네임을 매물에 복사해 둔다 — 탈퇴해도 화면에 남아야 한다.  */
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
                floorOf(request).raw(),
                floorOf(request).floorNo(),
                request.floorTotal(),
                floorOf(request).band(),
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
        // 매물이 어느 단지에 속하는지 적어 둔다 — 실거래는 단지에 붙는다
        complexService.attach(saved);
        agentService.upsertFromPaste(saved.id(), request.agent());
        eventPublisher.publishEvent(new PropertyCreatedEvent(saved.id()));
        editVersionStore.bump(versionKey(saved.id()));
        return toResponse(saved);
    }

    public PropertyResponse update(Long id, PropertyRequest request, Long editVersion) {
        validate(request);
        final Property existing = propertyAccessGuard.require(id);
        checkEditVersion(id, editVersion);
        final Coordinates coords = resolveCoordinatesForUpdate(existing, request);
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
                floorOf(request).raw(),
                floorOf(request).floorNo(),
                request.floorTotal(),
                floorOf(request).band(),
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
                        : Objects.equals(request.schoolName(), existing.schoolName())
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
        // 이름이나 주소를 고치면 단지가 바뀔 수 있다
        complexService.attach(updated);
        agentService.upsertFromPaste(id, request.agent());
        editVersionStore.bump(versionKey(id));
        // 매물 내용이 바뀌면 저장된 AI 분석을 갱신한다. 프롬프트 해시가
        // 실제 분석에 영향을 주는 변경인지 확인하므로 불필요한 호출은 생략한다.
        if (!existing.equals(updated)) {
            eventPublisher.publishEvent(PropertyInsightChanged.edited(id, currentNickname()));
        }
        return toResponse(updated);
    }

    public void delete(Long id) {
        final Property existing = propertyAccessGuard.require(id);
        propertyRepository.delete(id);
        // 지우고 나면 이름도 그룹도 알 수 없어 미리 담아 보낸다
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

    private FloorValue floorOf(PropertyRequest request) {
        return FloorValue.of(request.floorRaw())
                .orElseGet(() -> new FloorValue(
                        FloorValue.label(request.floorNo(), request.floorBand()),
                        request.floorNo(),
                        request.floorBand()));
    }

    private void validate(PropertyRequest request) {
        if (request.dealType() == null) {
            throw new InvalidPropertyRequestException("거래유형은 필수입니다");
        }
        // 적을 수 있는 것은 숫자와 저·중·고 뿐이다
        if (request.floorRaw() != null && !request.floorRaw().isBlank()
                && !FloorValue.isValid(request.floorRaw())) {
            throw new InvalidPropertyRequestException("층은 숫자 또는 저·중·고 로만 적을 수 있습니다");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidPropertyRequestException("매물명은 필수입니다");
        }
        validateAreas(request);
    }

    private void validateAreas(PropertyRequest request) {
        final BigDecimal supply = request.areaSupplyM2();
        final BigDecimal exclusive = request.areaExclusiveM2();
        if (supply == null || exclusive == null) {
            return;
        }
        if (exclusive.compareTo(supply) > 0) {
            throw new InvalidPropertyRequestException(
                    "전용면적(" + exclusive + "㎡)이 공급면적(" + supply + "㎡)보다 큽니다. "
                            + "두 값이 바뀌지 않았는지 확인해 주세요");
        }
    }

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
        final Coordinates base = new Coordinates(geo.get().lat(), geo.get().lng());
        return refineToBuilding(request, base);
    }

    private Coordinates resolveCoordinatesForUpdate(Property existing, PropertyRequest request) {
        final boolean buildingChanged = !Objects.equals(
                blankToNull(existing.dongHo()), blankToNull(request.dongHo()));
        if (!buildingChanged || movedByHand(existing, request)) {
            return resolveCoordinates(request);
        }
        final Coordinates base = existing.lat() == null || existing.lng() == null
                ? resolveCoordinates(new PropertyRequest(
                        request.name(), null, request.dealType(), request.priceDeposit(), null,
                        request.addressRoad(), request.addressJibun(), null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null))
                : new Coordinates(existing.lat(), existing.lng());
        if (base.lat() == null || base.lng() == null) {
            return base;
        }
        return refineToBuilding(request, base);
    }

    /** 사람이 좌표를 직접 고쳤는가 — 그랬다면 그 뜻을 존중한다.  */
    private static boolean movedByHand(Property existing, PropertyRequest request) {
        if (request.lat() == null || request.lng() == null) {
            return false;
        }
        return existing.lat() == null || existing.lng() == null
                || existing.lat().compareTo(request.lat()) != 0
                || existing.lng().compareTo(request.lng()) != 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Coordinates refineToBuilding(PropertyRequest request, Coordinates base) {
        return geoService.geocodeBuilding(request.name(), request.dongHo(), base.lat(), base.lng())
                .map(found -> new Coordinates(found.lat(), found.lng()))
                .orElse(base);
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

    /** 매물 카드에 등록자를 보여주기 위한 닉네임. 삭제된 사용자면 null.  */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::nickname).orElse(null);
    }
}
