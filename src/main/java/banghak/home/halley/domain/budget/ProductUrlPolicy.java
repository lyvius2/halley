package banghak.home.halley.domain.budget;

import java.net.URI;
import java.util.Set;

/** 상품 preview에 허용된 공개 판매 페이지 URL만 통과시킨다. */
public final class ProductUrlPolicy {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "ikea.com", "mujikorea.co.kr", "danawa.com", "nosearch.com", "lgecds.com",
            "shopping.naver.com", "samsung.com", "11st.co.kr", "coupang.com", "amazon.com", "amazon.co.jp", "aliexpress.com");

    public URI requireAllowed(String rawUrl) {
        final URI uri;
        try { uri = URI.create(rawUrl); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("invalid product url"); }
        final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || host.isBlank() || host.equals("localhost") || isIpLiteral(host)
                || ALLOWED_HOSTS.stream().noneMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed))) {
            throw new IllegalArgumentException("product url host is not allowed");
        }
        return uri;
    }

    private boolean isIpLiteral(String host) { return host.matches("[0-9.]+") || host.contains(":"); }
}
