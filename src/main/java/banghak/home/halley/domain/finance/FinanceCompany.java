package banghak.home.halley.domain.finance;

import java.util.List;

/** 금감원에 공시된 금융회사. */
public record FinanceCompany(
        FinanceGroup group,
        String dclsMonth,
        String finCoNo,
        String name,
        String homepageUrl,
        String callCenterTel,
        List<String> areas
) {

    public FinanceCompany {
        areas = areas == null ? List.of() : List.copyOf(areas);
    }
}
