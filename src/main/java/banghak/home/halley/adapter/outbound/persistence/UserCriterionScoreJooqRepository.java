package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.application.port.out.persistence.UserCriterionScoreRepository;
import banghak.home.halley.domain.scoring.UserCriterionScore;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserCriterionScoreTable.CRITERION_CODE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserCriterionScoreTable.PROPERTY_ID;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserCriterionScoreTable.SCORE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserCriterionScoreTable.TABLE;
import static banghak.home.halley.adapter.outbound.persistence.jdbc.UserCriterionScoreTable.USER_ID;

@Repository
public class UserCriterionScoreJooqRepository implements UserCriterionScoreRepository {

    private final DSLContext dsl;

    public UserCriterionScoreJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public UserCriterionScore save(UserCriterionScore score) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, score.propertyId())
                .set(USER_ID, score.userId())
                .set(CRITERION_CODE, score.criterionCode())
                .set(SCORE, score.score())
                .execute();
        return findById(score.propertyId(), score.userId(), score.criterionCode()).orElseThrow();
    }

    @Override
    public Optional<UserCriterionScore> findById(Long propertyId, Long userId, String criterionCode) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)).and(CRITERION_CODE.eq(criterionCode)))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<UserCriterionScore> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    @Override
    public List<UserCriterionScore> findByUserId(Long userId) {
        return dsl.selectFrom(TABLE)
                .where(USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

    @Override
    public void delete(Long propertyId, Long userId, String criterionCode) {
        dsl.deleteFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)).and(CRITERION_CODE.eq(criterionCode)))
                .execute();
    }

    private UserCriterionScore map(Record r) {
        return new UserCriterionScore(
                r.get(PROPERTY_ID),
                r.get(USER_ID),
                r.get(CRITERION_CODE),
                r.get(SCORE)
        );
    }
}
