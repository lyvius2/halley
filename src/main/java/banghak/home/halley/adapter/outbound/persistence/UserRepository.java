package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.ANNUAL_INCOME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.AVAILABLE_BUDGET;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.EXISTING_LOAN;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.CREATED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.DISABLED_AT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.DISABLED_BY;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.EMAIL;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.LOGIN_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.ENABLED;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.MUST_CHANGE_PASSWORD;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.NICKNAME;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.PASSWORD_HASH;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.ROLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.WORKPLACE_LAT;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.WORKPLACE_LNG;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserTable.WORKPLACE_NAME;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public User save(User user) {
        Instant now = Instant.now();
        Long id = dsl.insertInto(TABLE)
                .set(LOGIN_ID, user.loginId())
                .set(NICKNAME, user.nickname())
                .set(EMAIL, user.email())
                .set(PASSWORD_HASH, user.passwordHash())
                .set(ROLE, user.role().name())
                .set(WORKPLACE_NAME, user.workplaceName())
                .set(WORKPLACE_LAT, user.workplaceLat())
                .set(WORKPLACE_LNG, user.workplaceLng())
                .set(MUST_CHANGE_PASSWORD, user.mustChangePassword())
                .set(AVAILABLE_BUDGET, user.availableBudget())
                .set(ANNUAL_INCOME, user.annualIncomeOrZero())
                .set(EXISTING_LOAN, user.existingLoanOrZero())
                .set(ENABLED, user.enabled())
                .set(DISABLED_AT, toOffset(user.disabledAt()))
                .set(DISABLED_BY, user.disabledBy())
                .set(CREATED_AT, toOffset(user.createdAt() == null ? now : user.createdAt()))
                .returningResult(ID)
                .fetchOne()
                .component1();

        return new User(
                id,
                user.loginId(),
                user.nickname(),
                user.email(),
                user.passwordHash(),
                user.role(),
                user.workplaceName(),
                user.workplaceLat(),
                user.workplaceLng(),
                user.mustChangePassword(),
                user.availableBudget(),
                user.annualIncomeOrZero(),
                user.existingLoanOrZero(),
                user.enabled(),
                user.disabledAt(),
                user.disabledBy(),
                user.createdAt() == null ? now : user.createdAt()
        );
    }

    public User update(User user) {
        dsl.update(TABLE)
                .set(LOGIN_ID, user.loginId())
                .set(NICKNAME, user.nickname())
                .set(EMAIL, user.email())
                .set(PASSWORD_HASH, user.passwordHash())
                .set(ROLE, user.role().name())
                .set(WORKPLACE_NAME, user.workplaceName())
                .set(WORKPLACE_LAT, user.workplaceLat())
                .set(WORKPLACE_LNG, user.workplaceLng())
                .set(MUST_CHANGE_PASSWORD, user.mustChangePassword())
                .set(AVAILABLE_BUDGET, user.availableBudget())
                .set(ANNUAL_INCOME, user.annualIncomeOrZero())
                .set(EXISTING_LOAN, user.existingLoanOrZero())
                .set(ENABLED, user.enabled())
                .set(DISABLED_AT, toOffset(user.disabledAt()))
                .set(DISABLED_BY, user.disabledBy())
                .where(ID.eq(user.id()))
                .execute();
        return user;
    }

    public Optional<User> findById(Long id) {
        return dsl.selectFrom(TABLE)
                .where(ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    public Optional<User> findByLoginId(String loginId) {
        return dsl.selectFrom(TABLE)
                .where(LOGIN_ID.eq(loginId))
                .fetchOptional()
                .map(this::map);
    }

    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(TABLE)
                .where(EMAIL.eq(email))
                .fetchOptional()
                .map(this::map);
    }

    public Optional<User> findByNickname(String nickname) {
        return dsl.selectFrom(TABLE)
                .where(NICKNAME.eq(nickname))
                .fetchOptional()
                .map(this::map);
    }

    public List<User> findAll() {
        return dsl.selectFrom(TABLE)
                .fetch()
                .map(this::map);
    }

    public void delete(Long id) {
        dsl.deleteFrom(TABLE)
                .where(ID.eq(id))
                .execute();
    }

    private User map(Record r) {
        return new User(
                r.get(ID),
                r.get(LOGIN_ID),
                r.get(NICKNAME),
                r.get(EMAIL),
                r.get(PASSWORD_HASH),
                r.get(ROLE) == null ? null : UserRole.valueOf(r.get(ROLE)),
                r.get(WORKPLACE_NAME),
                r.get(WORKPLACE_LAT),
                r.get(WORKPLACE_LNG),
                Boolean.TRUE.equals(r.get(MUST_CHANGE_PASSWORD)),
                r.get(AVAILABLE_BUDGET),
                r.get(ANNUAL_INCOME),
                r.get(EXISTING_LOAN),
                Boolean.TRUE.equals(r.get(ENABLED)),
                toInstant(r.get(DISABLED_AT)),
                r.get(DISABLED_BY),
                toInstant(r.get(CREATED_AT))
        );
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
