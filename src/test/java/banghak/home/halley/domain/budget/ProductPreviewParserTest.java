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
}
