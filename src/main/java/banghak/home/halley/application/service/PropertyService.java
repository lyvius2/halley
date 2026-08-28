package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CheckLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.CreateDraftRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.ListingCheckLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.EditVersionStore;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.ConcurrentEditException;
import banghak.home.halley.domain.property.ListingCheckLog;
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
import java.util.Optional;

@Slf4j
@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AgentService agentService;
    private final ListingCheckLogRepository listingCheckLogRepository;
    private final EditVersionStore editVersionStore;
    private final GeoService geoService;
    private final ApplicationEventPublisher eventPublisher;

    public PropertyService(PropertyRepository propertyRepository,
                           UserRepository userRepository,
                           AgentService agentService,
                           ListingCheckLogRepository listingCheckLogRepository,
                           EditVersionStore editVersionStore,
                           GeoService geoService,
                           ApplicationEventPublisher eventPublisher) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.agentService = agentService;
        this.listingCheckLogRepository = listingCheckLogRepository;
        this.editVersionStore = editVersionStore;
        this.geoService = geoService;
        this.eventPublisher = eventPublisher;
    }

    public List<PropertyResponse> list() {
        return propertyRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PropertyResponse get(Long id) {
        return toResponse(propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new));
    }

    @Transactional
    public PropertyResponse create(PropertyRequest request) {
        validate(request);
        final Coordinates coords = resolveCoordinates(request);
        final boolean fromPaste = request.rawPasteText() != null && !request.rawPasteText().isBlank();
        final Property saved = propertyRepository.save(new Property(
                null,
                request.name(),
                request.dongHo(),
                request.dealType(),
                request.priceDeposit(),
                request.priceMonthly(),
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
                request.sourceUrl(),
                request.naverArticleNo(),
                request.rawPasteText(),
                fromPaste ? "parser-v1" : null,
                null,
                false,
                ListingStatus.ACTIVE,
                true,
                null, 0, null,
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
                null, name, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null,
                SourceType.PASTE, request.sourceUrl(), null, null, null, null,
                true, ListingStatus.ACTIVE, true,
                null, 0, null, currentUserId(), Instant.now()));
        return toResponse(saved);
    }

    public PropertyResponse update(Long id, PropertyRequest request, Long editVersion) {
        validate(request);
        final Property existing = propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new);
        checkEditVersion(id, editVersion);
        final Coordinates coords = resolveCoordinates(request);
        final Property updated = propertyRepository.update(new Property(
                existing.id(),
                request.name(),
                request.dongHo(),
                request.dealType(),
                request.priceDeposit(),
                request.priceMonthly(),
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
                existing.sourceUrl(),
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
                existing.createdBy(),
                existing.createdAt()));
        agentService.upsertFromPaste(id, request.agent());
        editVersionStore.bump(versionKey(id));
        return toResponse(updated);
    }

    public void delete(Long id) {
        propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new);
        propertyRepository.delete(id);
    }

    public PropertyResponse updateStatus(Long id, ListingStatus listingStatus) {
        if (listingStatus == null) {
            throw new InvalidPropertyRequestException("판매 상태는 필수입니다");
        }
        final Property existing = propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new);
        final boolean active = listingStatus != ListingStatus.SOLD_OUT && listingStatus != ListingStatus.ARCHIVED;
        propertyRepository.updateListingStatus(
                id,
                listingStatus,
                active,
                listingStatus == ListingStatus.ACTIVE ? 0 : existing.checkFailStreak(),
                listingStatus == ListingStatus.SOLD_OUT ? Instant.now() : null);
        return get(id);
    }

    public List<CheckLogResponse> checkLogs(Long id) {
        propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new);
        return listingCheckLogRepository.findByPropertyId(id).stream()
                .map(this::toCheckLogResponse)
                .toList();
    }

    public List<PropertyResponse> recentSoldOut() {
        return propertyRepository.findRecentSoldOut(10).stream()
                .map(this::toResponse)
                .toList();
    }

    private CheckLogResponse toCheckLogResponse(ListingCheckLog log) {
        return new CheckLogResponse(
                log.id(), log.checkedAt(), log.httpStatus(), log.verdict(),
                log.evidence(), log.elapsedMs());
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
