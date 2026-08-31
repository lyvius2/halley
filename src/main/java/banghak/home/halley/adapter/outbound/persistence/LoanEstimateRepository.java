package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.loan.LoanEstimate;
import banghak.home.halley.domain.loan.ProductType;
import tools.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.ACQUISITION_TAX;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.ASSUMPTIONS;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.ASSUMPTIONS_RAW;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.COMPUTED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.DSR_LIMIT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.FINAL_LIMIT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.LTV_LIMIT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.LTV_RATE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.PRODUCT_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.REQUIRED_CASH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.LoanEstimateTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJson;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toJsonNode;

@Repository
public class LoanEstimateRepository {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public LoanEstimateRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public LoanEstimate save(LoanEstimate estimate) {
        Long id = dsl.insertInto(TABLE)
                .set(PROPERTY_ID, estimate.propertyId())
                .set(PRODUCT_TYPE, estimate.productType() == null ? null : estimate.productType().name())
                .set(LTV_RATE, estimate.ltvRate())
                .set(LTV_LIMIT, estimate.ltvLimit())
                .set(DSR_LIMIT, estimate.dsrLimit())
                .set(FINAL_LIMIT, estimate.finalLimit())
                .set(REQUIRED_CASH, estimate.requiredCash())
                .set(ACQUISITION_TAX, estimate.acquisitionTax())
                .set(ASSUMPTIONS, toJson(estimate.assumptions(), objectMapper))
                .returningResult(ID)
                .fetchOne()
                .component1();
        return findById(id).orElseThrow();
    }

    public Optional<LoanEstimate> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public List<LoanEstimate> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private LoanEstimate map(Record r) {
        return new LoanEstimate(
                r.get(ID),
                r.get(PROPERTY_ID),
                toEnum(ProductType.class, r.get(PRODUCT_TYPE)),
                r.get(LTV_RATE),
                r.get(LTV_LIMIT),
                r.get(DSR_LIMIT),
                r.get(FINAL_LIMIT),
                r.get(REQUIRED_CASH),
                r.get(ACQUISITION_TAX),
                toJsonNode(r.get(ASSUMPTIONS_RAW), objectMapper),
                toInstant(r.get(COMPUTED_AT))
        );
    }
}
