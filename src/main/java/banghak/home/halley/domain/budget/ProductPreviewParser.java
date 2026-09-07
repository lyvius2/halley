package banghak.home.halley.domain.budget;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JSON-LD·Open Graph·title 순서로 단일 상품 페이지의 공개 메타데이터를 읽는다. */
public final class ProductPreviewParser {
    private static final Pattern JSON_NAME = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern JSON_PRICE = Pattern.compile("\\\"price\\\"\\s*:\\s*\\\"?([0-9,]+)");
    private static final Pattern OG_NAME = Pattern.compile("property=[\\\"]og:title[\\\"][^>]*content=[\\\"]([^\\\"]+)|content=[\\\"]([^\\\"]+)[\\\"][^>]*property=[\\\"]og:title", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_PRICE = Pattern.compile("property=[\\\"]product:price:amount[\\\"][^>]*content=[\\\"]([0-9,]+)|content=[\\\"]([0-9,]+)[\\\"][^>]*property=[\\\"]product:price:amount", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public ProductPreview parse(String html) {
        if (html == null || html.isBlank()) return ProductPreview.empty();
        final String name = first(JSON_NAME, html, 1, 1);
        final Long price = number(first(JSON_PRICE, html, 1, 1));
        if (name != null || price != null) return new ProductPreview(name, price, true);
        final String ogName = first(OG_NAME, html, 1, 2);
        final Long ogPrice = number(first(OG_PRICE, html, 1, 2));
        if (ogName != null || ogPrice != null) return new ProductPreview(ogName, ogPrice, true);
        return new ProductPreview(first(TITLE, html, 1, 1), null, false);
    }

    private String first(Pattern pattern, String source, int... groups) { final Matcher m = pattern.matcher(source); if (!m.find()) return null; for (int group : groups) if (m.group(group) != null) return m.group(group).trim(); return null; }
    private Long number(String value) { if (value == null) return null; try { return Long.parseLong(value.replace(",", "")); } catch (NumberFormatException e) { return null; } }
}
