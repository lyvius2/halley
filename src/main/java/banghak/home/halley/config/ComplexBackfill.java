package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.service.ComplexService;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.COMPLEX_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.PropertyTable.TABLE;

/** 기존 매물의 단지 정보를 보완한다. */
@Slf4j
@Component
@Order(50)
public class ComplexBackfill implements ApplicationRunner {

    private final DSLContext dsl;
    private final PropertyRepository propertyRepository;
    private final ComplexService complexService;

    public ComplexBackfill(DSLContext dsl, PropertyRepository propertyRepository,
                           ComplexService complexService) {
        this.dsl = dsl;
        this.propertyRepository = propertyRepository;
        this.complexService = complexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        final List<Long> pending = dsl.select(ID)
                .from(TABLE)
                .where(COMPLEX_ID.isNull())
                .fetch(ID);
        if (pending.isEmpty()) {
            return;
        }
        int attached = 0;
        for (final Long id : pending) {
            final Property property = propertyRepository.findById(id).orElse(null);
            if (property == null) {
                continue;
            }
            try {
                complexService.attach(property);
                attached++;
            } catch (RuntimeException e) {
                log.warn("Could not attach complex. propertyId={}, cause={}", id, e.toString());
            }
        }
        log.info("Attached {} properties to their complex. pending={}", attached, pending.size());
    }
}
