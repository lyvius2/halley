package banghak.home.halley.domain.itinerary;

import java.util.ArrayList;
import java.util.List;

/**
 * Held-Karp 정확해 알고리즘 — 최대 12개 매물(설계 I38 하드 캡)에 대해 최소 총 이동시간 방문 순서를 구한다.
 * 노드 0은 출발지(depot), 이후 인덱스는 목적지 매물에 대응한다.
 */
public final class ItineraryOptimizer {

    public List<Long> optimize(long startId, List<Long> nodeIds, TravelCostMatrix matrix) {
        final int n = nodeIds.size();
        if (n == 0) {
            return List.of();
        }
        final int size = 1 << n;
        final int full = size - 1;
        final long[][] dp = new long[size][n];
        final int[][] prev = new int[size][n];

        for (int mask = 0; mask < size; mask++) {
            for (int j = 0; j < n; j++) {
                dp[mask][j] = Long.MAX_VALUE / 2;
                prev[mask][j] = -1;
            }
        }
        for (int i = 0; i < n; i++) {
            dp[1 << i][i] = matrix.minutes(startId, nodeIds.get(i));
        }
        for (int mask = 1; mask < size; mask++) {
            for (int j = 0; j < n; j++) {
                if ((mask & (1 << j)) == 0) {
                    continue;
                }
                final int without = mask ^ (1 << j);
                for (int i = 0; i < n; i++) {
                    if (i == j || (without & (1 << i)) == 0) {
                        continue;
                    }
                    final long candidate = dp[without][i] + matrix.minutes(nodeIds.get(i), nodeIds.get(j));
                    if (candidate < dp[mask][j]) {
                        dp[mask][j] = candidate;
                        prev[mask][j] = i;
                    }
                }
            }
        }

        int last = -1;
        long best = Long.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            if (dp[full][j] < best) {
                best = dp[full][j];
                last = j;
            }
        }

        final List<Long> order = new ArrayList<>(n);
        int mask = full;
        int current = last;
        while (current != -1) {
            order.add(0, nodeIds.get(current));
            final int next = prev[mask][current];
            mask = mask ^ (1 << current);
            current = next;
        }
        return List.copyOf(order);
    }
}
