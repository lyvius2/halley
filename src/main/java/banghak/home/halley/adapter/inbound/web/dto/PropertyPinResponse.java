package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.property.DealType;

import java.math.BigDecimal;

/**
 * 지도에 찍을 것만 담은 매물 (설계 I240).
 *
 * <p>목록은 30건씩 잘라 보내지만 <b>지도와 임장 플래너는 전부</b> 알아야 합니다.
 * 잘린 목록으로 지도를 그리면 <b>매물이 사라진 것처럼</b> 보이고, 그것이 이 프로젝트가
 * 가장 자주 겪은 사고입니다([I221]에서 판매완료 숨김을 걷어낸 이유도 같습니다).
 *
 * <p>그렇다고 채점까지 붙은 전체 목록을 따로 받으면 자른 보람이 없습니다. 지도가
 * 실제로 쓰는 값만 담습니다 — 좌표·이름·면적·가격·거래유형, 그리고 흐리게 칠할지.
 *
 * @param visited          가 봤는가 (설계 I226). <b>서버가 판단합니다</b> — 방문 기록이 있거나
 *                         쾌적함을 매겼으면 가 본 것입니다. 화면에서 두 조건을 다시 합치면
 *                         <b>규칙이 두 벌</b>이 됩니다
 * @param visitedByComfort 그 판단이 <b>쾌적함 채점에서</b> 왔는가 (설계 I228).
 *                         그렇다면 체크를 끌 수 없습니다 — 근거가 채점이라 끄려면
 *                         그 점수를 지워야 하고, 그건 채점 화면의 일입니다
 * @param active           판매중인가. 비교 우위 분석의 대상을 세는 데 씁니다
 * @param draft            작성 중인가. 위와 같습니다
 */
public record PropertyPinResponse(
        Long id,
        String name,
        DealType dealType,
        Long priceDeposit,
        BigDecimal areaExclusiveM2,
        BigDecimal lat,
        BigDecimal lng,
        boolean visited,
        boolean visitedByComfort,
        boolean active,
        boolean draft
) {
}
