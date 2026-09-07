package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.budget.BudgetFinancing;
import banghak.home.halley.domain.budget.RepaymentType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.HouseholdBudgetFinancingTable.*;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toInstant;

@Repository
public class HouseholdBudgetFinancingRepository {
    private final DSLContext dsl;
    public HouseholdBudgetFinancingRepository(DSLContext dsl) { this.dsl = dsl; }
    public BudgetFinancing save(BudgetFinancing financing) {
        final Long id = dsl.insertInto(TABLE).set(PLAN_ID, financing.planId()).set(LOAN_AMOUNT, financing.loanAmount()).set(LOAN_RATIO, financing.loanRatio()).set(INTEREST_RATE, financing.interestRate()).set(TERM_MONTHS, financing.termMonths()).set(REPAYMENT_TYPE, financing.repaymentType().name()).set(MONTHLY_PAYMENT, financing.monthlyPayment()).set(IS_ESTIMATE, financing.estimate()).returningResult(ID).fetchOne().component1();
        return findById(id).orElseThrow();
    }
    public Optional<BudgetFinancing> findById(Long id) { return dsl.selectFrom(TABLE).where(ID.eq(id)).fetchOptional().map(this::map); }
    public Optional<BudgetFinancing> findByPlanId(Long planId) { return dsl.selectFrom(TABLE).where(PLAN_ID.eq(planId)).fetchOptional().map(this::map); }
    public void deleteByPlanId(Long planId) { dsl.deleteFrom(TABLE).where(PLAN_ID.eq(planId)).execute(); }
    public void delete(Long id) { dsl.deleteFrom(TABLE).where(ID.eq(id)).execute(); }
    private BudgetFinancing map(Record r) { return new BudgetFinancing(r.get(ID), r.get(PLAN_ID), r.get(LOAN_AMOUNT), r.get(LOAN_RATIO), r.get(INTEREST_RATE), r.get(TERM_MONTHS), toEnum(RepaymentType.class, r.get(REPAYMENT_TYPE)), r.get(MONTHLY_PAYMENT), r.get(IS_ESTIMATE), toInstant(r.get(CREATED_AT)), toInstant(r.get(UPDATED_AT))); }
}
