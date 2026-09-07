package banghak.home.halley.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductPreviewParserTest {
    @Test @DisplayName("JSON-LD 상품명과 가격을 우선 읽는다") void parsesJsonLd() {
        // given
        final String html = "<script>{\"@type\":\"Product\",\"name\":\"원목 식탁\",\"price\":\"349000\"}</script>";
        // when
        final ProductPreview preview = new ProductPreviewParser().parse(html);
        // then
        assertThat(preview.productName()).isEqualTo("원목 식탁"); assertThat(preview.priceWon()).isEqualTo(349000L);
    }
    @Test @DisplayName("허용 도메인 밖과 IP 주소 상품 URL을 막는다") void rejectsUnsafeUrl() {
        // given
        final ProductUrlPolicy policy = new ProductUrlPolicy();
        // when // then
        assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("http://127.0.0.1/private"));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("https://example.com/product"));
    }
    @Test @DisplayName("허용된 쇼핑몰 상품 URL을 통과시킨다") void allowsApprovedShoppingUrls() {
        // given
        final ProductUrlPolicy policy = new ProductUrlPolicy();
        // when
        final var naver = policy.requireAllowed("https://shopping.naver.com/product/123");
        final var samsung = policy.requireAllowed("https://www.samsung.com/kr/refrigerators/123");
        final var eleven = policy.requireAllowed("https://www.11st.co.kr/products/123");
        final var coupang = policy.requireAllowed("https://www.coupang.com/vp/products/123");
        final var amazonUs = policy.requireAllowed("https://www.amazon.com/dp/B000000000");
        final var amazonJp = policy.requireAllowed("https://www.amazon.co.jp/dp/B000000000");
        final var aliexpress = policy.requireAllowed("https://www.aliexpress.com/item/1005000000000.html");
        // then
        assertThat(naver.getHost()).isEqualTo("shopping.naver.com");
        assertThat(samsung.getHost()).isEqualTo("www.samsung.com");
        assertThat(eleven.getHost()).isEqualTo("www.11st.co.kr");
        assertThat(coupang.getHost()).isEqualTo("www.coupang.com");
        assertThat(amazonUs.getHost()).isEqualTo("www.amazon.com");
        assertThat(amazonJp.getHost()).isEqualTo("www.amazon.co.jp");
        assertThat(aliexpress.getHost()).isEqualTo("www.aliexpress.com");
    }
}
