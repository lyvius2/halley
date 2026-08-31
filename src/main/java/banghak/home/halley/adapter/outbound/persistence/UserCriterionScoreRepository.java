package banghak.home.halley.adapter.outbound.persistence;

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
public class UserCriterionScoreRepository {

    private final DSLContext dsl;

    public UserCriterionScoreRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public UserCriterionScore save(UserCriterionScore score) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, score.propertyId())
                .set(USER_ID, score.userId())
                .set(CRITERION_CODE, score.criterionCode())
                .set(SCORE, score.score())
                .execute();
        return findById(score.propertyId(), score.userId(), score.criterionCode()).orElseThrow();
    }

    public void upsert(UserCriterionScore score) {
        dsl.insertInto(TABLE)
                .set(PROPERTY_ID, score.propertyId())
                .set(USER_ID, score.userId())
                .set(CRITERION_CODE, score.criterionCode())
                .set(SCORE, score.score())
                .onConflict(PROPERTY_ID, USER_ID, CRITERION_CODE)
                .doUpdate()
                .set(SCORE, score.score())
                .execute();
    }

    public Optional<UserCriterionScore> findById(Long propertyId, Long userId, String criterionCode) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId).and(USER_ID.eq(userId)).and(CRITERION_CODE.eq(criterionCode)))
                .fetchOptional()
                .map(this::map);
    }

    /**
     * 매물 여러 건을 한 번에 (설계 I124).
     *
     * <p>목록 화면이 매물마다 따로 부르면 그 수만큼 왕복이 늘어납니다 — 느린 DB에서는
     * 그것이 그대로 체감 지연이 됩니다. 비어 있으면 질의하지 않습니다:
     * {@code IN ()} 는 dialect마다 다르게 굴어 굳이 시험할 이유가 없습니다.
     */
    public List<UserCriterionScore> findByPropertyIds(java.util.Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.in(propertyIds))
                .fetch()
                .map(this::map);
    }

    public List<UserCriterionScore> findByPropertyId(Long propertyId) {
        return dsl.selectFrom(TABLE)
                .where(PROPERTY_ID.eq(propertyId))
                .fetch()
                .map(this::map);
    }

    public List<UserCriterionScore> findByUserId(Long userId) {
        return dsl.selectFrom(TABLE)
                .where(USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

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
