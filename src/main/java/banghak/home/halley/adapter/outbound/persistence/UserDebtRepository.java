package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.loan.DebtType;
import banghak.home.halley.domain.loan.ExistingDebt;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserDebtTable.AMOUNT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserDebtTable.DEBT_TYPE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserDebtTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserDebtTable.USER_ID;
import static banghak.home.halley.adapter.outbound.persistence.support.JooqMapping.toEnum;

@Repository
public class UserDebtRepository {

    private final DSLContext dsl;

    public UserDebtRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ExistingDebt> findByUserId(Long userId) {
        return userId == null ? List.of()
                : dsl.selectFrom(TABLE)
                        .where(USER_ID.eq(userId))
                        .orderBy(DEBT_TYPE.asc())
                        .fetch()
                        .map(this::map);
    }

    /**
     * 한 사람의 부채를 통째로 갈아 끼운다.
     *
     * <p>0원짜리는 <b>저장하지 않고 지웁니다.</b> 남겨 두면 화면에 빈 줄이 쌓이고,
     * 계산에도 의미가 없습니다.
     */
    public void replaceAll(Long userId, List<ExistingDebt> debts) {
        dsl.deleteFrom(TABLE).where(USER_ID.eq(userId)).execute();
        for (final ExistingDebt debt : debts) {
            if (debt == null || debt.type() == null || debt.amount() <= 0L) {
                continue;
            }
            dsl.insertInto(TABLE)
                    .set(USER_ID, userId)
                    .set(DEBT_TYPE, debt.type().name())
                    .set(AMOUNT, debt.amount())
                    .onConflict(USER_ID, DEBT_TYPE)
                    .doUpdate()
                    .set(AMOUNT, debt.amount())
                    .execute();
        }
    }

    public void deleteByUserId(Long userId) {
        dsl.deleteFrom(TABLE).where(USER_ID.eq(userId)).execute();
    }

    private ExistingDebt map(Record r) {
        return new ExistingDebt(toEnum(DebtType.class, r.get(DEBT_TYPE)),
                r.get(AMOUNT) == null ? 0L : r.get(AMOUNT));
    }
}
