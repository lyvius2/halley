package banghak.home.halley.domain.scoring.criterion;

import banghak.home.halley.domain.property.Property;

public class PriceScorer implements CriterionScorer {

    @Override
    public String code() {
        return "PRICE";
    }


    @Override
    public ScoreResult score(Property property, ScoringContext ctx) {
        final Long askingPrice = property.priceDeposit();
        if (askingPrice == null || askingPrice <= 0) {
            return ScoreResult.missing("호가 없음");
        }
        if (ctx.cashBudget() <= 0) {
            // 예산상한 = 현금 + 대출한도(호가×LTV)이므로, 현금이 0이면 상한이 늘 호가보다 작아
            // 모든 매물이 0점으로 붕괴한다. 점수를 매기는 대신 설정이 빠졌음을 알린다.
            return ScoreResult.missing("가용 예산 미설정 — 사용자 관리에서 가용 예산을 입력하세요");
        }
        final long budget = ctx.cashBudget() + ctx.loanCalculator().expectedLoanLimit(askingPrice);
        if (budget <= 0) {
            return ScoreResult.missing("예산상한을 계산할 수 없습니다");
        }
        final long loanLimit = ctx.loanCalculator().expectedLoanLimit(askingPrice);
        final double targetValue = 100.0 * (1.0 - (double) askingPrice / budget);
        return ScoreResult.scored(Math.clamp(targetValue, 0.0, 100.0), String.format(
                "호가 %s / 예산상한 %s (현금 %s + 예상 대출한도 %s) → 100 × (1 − 호가/예산상한)%s",
                won(askingPrice), won(budget), won(ctx.cashBudget()), won(loanLimit),
                targetValue < 0 ? ", 예산상한을 넘어 0점" : ""));
    }

    /** 전망 근거(`TradeTrendIndicator`)와 같은 표기를 쓴다 (설계 I130). */
    private static String won(long amount) {
        return banghak.home.halley.domain.support.WonFormat.of(amount);
    }
}
