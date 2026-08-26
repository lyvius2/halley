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
        final Integer totalSeconds = asInteger(info.path("totalTime"));
        final Integer walkSeconds = asInteger(info.path("walkTime"));
        final int transferCount = nz(asInteger(info.path("subwayTransitCount")))
                + nz(asInteger(info.path("busTransitCount")));
        return new TransitResult(toMinutes(totalSeconds), transferCount, toMinutes(walkSeconds));
    }

    private static Integer asInteger(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static Integer toMinutes(Integer seconds) {
        return seconds == null ? null : (seconds + 59) / 60;
    }

    public boolean isComputed() {
        return totalMinutes != null;
    }
}
