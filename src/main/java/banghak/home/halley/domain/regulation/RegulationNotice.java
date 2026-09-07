package banghak.home.halley.domain.regulation;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.LocalDate;
import java.util.List;

/**
 * 국토교통부 규제지역 지정 고시 한 건.
 *
 * areaNames는 고시 첨부 PDF의 지정 현황표에서 뽑은 것으로, 그 시점의
 * 전체 현황입니다. 고시 본문(`제개정이유`)에는 이번에 추가된 지역만 있어 그것만 반영하면
 * 해제된 지역이 남습니다.
 *
 * 이름은 고시가 쓰는 축약형 그대로입니다 — 화성동탄(화성시 동탄구), 성남분당.
 * 법정동코드로 바꾸는 일은 SigunguNameMatcher가 맡습니다.
 *
 * @param noticeNo    공고번호 (예: `2026-883`) — 왜 이 값이 들어왔는지 남기는 근거
 * @param announcedOn 발령일자. 이 값이 바뀌면 고시가 갱신된 것이다
 */
public record RegulationNotice(
        RegulationZone zone,
        String noticeNo,
        LocalDate announcedOn,
        List<String> areaNames
) {

    public boolean hasAreas() {
        return areaNames != null && !areaNames.isEmpty();
    }
}
