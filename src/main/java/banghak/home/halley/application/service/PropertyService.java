package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public List<PropertyResponse> list() {
        return propertyRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PropertyResponse get(Long id) {
        return toResponse(propertyRepository.findById(id)
                .orElseThrow(NotFoundListingsException::new));
    }

    public PropertyResponse create(PropertyRequest request) {
        validate(request);
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
                SourceType.MANUAL,
                null, null, null, null, null,
                false,
                ListingStatus.ACTIVE,
                true,
                null, 0, null,
                currentUserId(),
                Instant.now()));
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
        return new PropertyResponse(
                p.id(), p.name(), p.dongHo(), p.dealType(),
                p.priceDeposit(), p.priceMonthly(), p.maintenanceFee(),
                p.addressRoad(), p.addressJibun(), p.lat(), p.lng(),
                p.areaSupplyM2(), p.areaExclusiveM2(), p.floorRaw(), p.floorNo(), p.floorTotal(), p.floorBand(),
                p.roomBath(), p.direction(), p.approvalYear(), p.moveInType(), p.moveInDate(),
                p.parkingPerHousehold(), p.totalHouseholds(), p.heatingType(), p.buildingCount(), p.kbPrice(),
                p.sourceType(), p.listingStatus(), p.active(), p.createdAt());
    }
}
