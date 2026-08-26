package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.dto.CheckLogResponse;
import banghak.home.halley.application.event.PropertyCreatedEvent;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.ListingCheckLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.property.ListingCheckLog;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final ListingCheckLogRepository listingCheckLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PropertyService(PropertyRepository propertyRepository,
                           ListingCheckLogRepository listingCheckLogRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.propertyRepository = propertyRepository;
        this.listingCheckLogRepository = listingCheckLogRepository;
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
                request.lat(),
                request.lng(),
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
        eventPublisher.publishEvent(new PropertyCreatedEvent(saved.id()));
        return toResponse(saved);
    }

    public PropertyResponse update(Long id, PropertyRequest request) {
        validate(request);
        final Property existing = propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new);
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
                request.lat(),
                request.lng(),
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
                existing.sourceType(),
                existing.sourceUrl(),
                existing.naverArticleNo(),
                existing.rawPasteText(),
                existing.parserVersion(),
                existing.parseConfidence(),
                existing.isDraft(),
                existing.listingStatus(),
                existing.active(),
                existing.lastCheckedAt(),
                existing.checkFailStreak(),
                existing.soldDetectedAt(),
                existing.createdBy(),
                existing.createdAt()));
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

    private Long currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return principal.getId();
        }
        return null;
    }

    private PropertyResponse toResponse(Property p) {
        return PropertyResponse.from(p);
    }
}
