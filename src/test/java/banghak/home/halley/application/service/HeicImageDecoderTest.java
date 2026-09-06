package banghak.home.halley.application.service;

import banghak.home.halley.config.exception.InvalidPropertyImageException;
import banghak.home.halley.support.HeicTestImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeicImageDecoderTest {

    private final HeicImageDecoder decoder = new HeicImageDecoder();

    @Test
    @DisplayName("확장자가 JPEG여도 파일 내용이 HEIC이면 판별하고 디코딩한다")
    void detectsAndDecodesHeicByContent() throws Exception {
        // given
        final MockMultipartFile file = new MockMultipartFile(
                "file", "iphone.jpeg", "image/jpeg", HeicTestImage.bytes());

        // when
        final boolean matches = decoder.matches(file);
        final BufferedImage image = decoder.decode(file);

        // then
        assertThat(matches).isTrue();
        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
    }

    @Test
    @DisplayName("HEIC 시그니처만 있고 이미지가 손상됐으면 이미지 오류로 거부한다")
    void rejectsBrokenHeic() throws Exception {
        // given
        final byte[] broken = Arrays.copyOf(HeicTestImage.bytes(), 64);
        final MockMultipartFile file = new MockMultipartFile("file", "broken.heic", "image/heic", broken);

        // when
        final InvalidPropertyImageException thrown = assertThrows(
                InvalidPropertyImageException.class, () -> decoder.decode(file));

        // then
        assertThat(thrown.getCode()).isEqualTo("PROPERTY_IMAGE_INVALID");
    }
}
