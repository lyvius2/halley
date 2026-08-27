package banghak.home.halley.domain.scoring;

import tools.jackson.databind.JsonNode;

public record TransitResult(
        Integer totalMinutes,
        Integer transferCount,
        Integer walkMinutes
) {
    public static TransitResult missing() {
        return new TransitResult(null, null, null);
    }

    public static TransitResult mapResult(JsonNode root) {
        final JsonNode info = root.path("result").path("path").path(0).path("info");
        if (info.isMissingNode() || info.isNull()) {
            return TransitResult.missing();
        }
        final Integer totalMinutes = asInteger(info.path("totalTime"));
        final int transferCount = nz(asInteger(info.path("subwayTransitCount")))
                + nz(asInteger(info.path("busTransitCount")));
        return new TransitResult(totalMinutes, transferCount, resolveWalkMinutes(info));
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
