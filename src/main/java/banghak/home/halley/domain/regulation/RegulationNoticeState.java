package banghak.home.halley.domain.regulation;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 규제별로 마지막에 반영한 고시와 그 적재 결과 (설계 I73).
 *
 * @param announcedOn 반영한 고시의 발령일자. 새로 조회한 값이 이와 다르면 고시가 갱신된 것이다
 * @param message     실패했을 때 왜인지 — 남기지 않으면 로그를 뒤져야 한다
 */
public record RegulationNoticeState(
        RegulationZone zone,
        String noticeNo,
        LocalDate announcedOn,
        RegulationSeedStatus seedStatus,
        int areaCount,
        String message,
        Instant updatedAt
) {

    public static RegulationNoticeState notStarted(RegulationZone zone) {
        return new RegulationNoticeState(
                zone, null, null, RegulationSeedStatus.NOT_STARTED, 0, null, null);
    }

    /** 새 고시가 나왔는지. 발령일자가 다르면 갈아 끼워야 한다. */
    public boolean isOutdatedBy(RegulationNotice notice) {
        return notice.announcedOn() != null && !notice.announcedOn().equals(announcedOn);
    }
}
