package banghak.home.halley.domain.scoring;

import banghak.home.halley.domain.itinerary.TransitLeg;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @param legs   구간 상세 (설계 I176). ODsay의 `subPath` — 몇 호선을 어디서 어디까지 탔는지.
 *               <b>채점에는 안 쓰고 화면에만 씁니다</b> — 직주근접 점수는 총 시간만 봅니다
 * @param mapObj 경로선을 받아 올 때 쓰는 열쇠 (설계 I177). ODsay `loadLane` 에 넘긴다
 * @param estimated ODsay 가 아니라 <b>LLM 이 추정한 값</b>인가 (설계 I210).
 *                  <b>사실이 값과 함께 다녀야 합니다</b> — 저장하는 쪽이 출처를 남기고,
 *                  다음에 ODsay 가 살아나면 진짜 값으로 갈아 끼울 수 있습니다.
 *                  이걸 안 달면 추정이 <b>영영 진짜인 척</b> 남습니다
 */
public record TransitResult(
        Integer totalMinutes,
        Integer transferCount,
        Integer walkMinutes,
        List<TransitLeg> legs,
        String mapObj,
        boolean estimated
) {
    /** ODsay 에서 온 값 — 추정이 아니다. */
    public TransitResult(Integer totalMinutes, Integer transferCount, Integer walkMinutes,
                         List<TransitLeg> legs, String mapObj) {
        this(totalMinutes, transferCount, walkMinutes, legs, mapObj, false);
    }

    /** 구간 상세가 필요 없는 자리를 위한 간편 생성 — 채점은 총 시간만 봅니다. */
    public TransitResult(Integer totalMinutes, Integer transferCount, Integer walkMinutes) {
        this(totalMinutes, transferCount, walkMinutes, List.of(), null, false);
    }

    /** LLM 이 추정한 값 (설계 I210). */
    public static TransitResult estimated(Integer totalMinutes, Integer transferCount,
                                          Integer walkMinutes, List<TransitLeg> legs) {
        return new TransitResult(totalMinutes, transferCount, walkMinutes, legs, null, true);
    }

    public static TransitResult missing() {
        return new TransitResult(null, null, null, List.of(), null, false);
    }

    public static TransitResult mapResult(JsonNode root) {
        final JsonNode info = root.path("result").path("path").path(0).path("info");
        if (info.isMissingNode() || info.isNull()) {
            return TransitResult.missing();
        }
        final JsonNode path = root.path("result").path("path").path(0);
        final Integer totalMinutes = asInteger(info.path("totalTime"));
        final int transferCount = nz(asInteger(info.path("subwayTransitCount")))
                + nz(asInteger(info.path("busTransitCount")));
        return new TransitResult(totalMinutes, transferCount, resolveWalkMinutes(info),
                legsOf(path.path("subPath")), info.path("mapObj").asString(null));
    }

    /**
     * `subPath` → 구간 목록 (설계 I176).
     *
     * <p><b>0분짜리 도보는 버립니다.</b> ODsay 는 환승 통로도 도보 구간으로 주는데,
     * "걸어서 0분"을 줄줄이 늘어놓으면 정작 <b>몇 호선을 타는지가 안 보입니다.</b>
     */
    private static List<TransitLeg> legsOf(JsonNode subPath) {
        if (!subPath.isArray()) {
            return List.of();
        }
        final List<TransitLeg> legs = new ArrayList<>();
        for (final JsonNode sp : subPath) {
            final TransitLeg.Kind kind = TransitLeg.kindOf(sp.path("trafficType").asInt(3));
            final Integer minutes = asInteger(sp.path("sectionTime"));
            if (kind == TransitLeg.Kind.WALK) {
                if (minutes != null && minutes > 0) {
                    legs.add(new TransitLeg(kind, null, null, null, minutes, null));
                }
                continue;
            }
            legs.add(new TransitLeg(kind, laneName(sp.path("lane")),
                    sp.path("startName").asString(null), sp.path("endName").asString(null),
                    minutes, asInteger(sp.path("stationCount"))));
        }
        return legs;
    }

    /** 지하철은 `name`, 버스는 `busNo`. 여러 노선이 오면 <b>첫 번째만</b> 씁니다. */
    private static String laneName(JsonNode lane) {
        if (!lane.isArray() || lane.isEmpty()) {
            return null;
        }
        final JsonNode first = lane.path(0);
        final String name = first.path("name").asString(null);
        return name != null ? name : first.path("busNo").asString(null);
    }

    private static Integer resolveWalkMinutes(JsonNode info) {
        final Integer walkTime = asInteger(info.path("totalWalkTime"));
        if (walkTime != null && walkTime >= 0) {
            return walkTime;
        }
        final Integer walkMeters = asInteger(info.path("totalWalk"));
        return walkMeters == null ? null : Math.max(1, (walkMeters + 79) / 80);
    }

    private static Integer asInteger(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    public boolean isComputed() {
        return totalMinutes != null;
    }
}
