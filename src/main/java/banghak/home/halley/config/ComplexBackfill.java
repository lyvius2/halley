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

/**
 * 이미 있는 매물에 단지를 달아 준다 (설계 I266). 단지 열쇠 생성 규칙이 자바(`ComplexName`)에
 * 있어 SQL 로는 못 한다. 이미 달린 매물은 안 건드리므로 여러 번 돌려도 같다.
 */
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
                // 한 건이 막혀도 나머지는 달아 준다 — 안 달린 것은 다음 기동에 다시 본다
                log.warn("Could not attach complex. propertyId={}, cause={}", id, e.toString());
            }
        }
        log.info("Attached {} properties to their complex (설계 I266). pending={}", attached, pending.size());
    }
}
