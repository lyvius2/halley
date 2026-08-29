package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyImageResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ImageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "app.images.dir=${java.io.tmpdir}/halley-test-uploads")
class PropertyImageServiceTest {

    @Autowired
    private PropertyImageService propertyImageService;

    @Autowired
    private PropertyService propertyService;

    @Test
    @DisplayName("이미지를 업로드하면 원본(최대 1920)·썸네일(320)로 저장하고 목록에 노출한다")
    void uploadAndList() throws Exception {
        // given
        final PropertyResponse property = propertyService.create(request());
        final byte[] jpeg = jpegBytes();

        // when
        final PropertyImageResponse uploaded = propertyImageService.upload(
                property.id(), new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg), ImageType.PHOTO);
        final List<PropertyImageResponse> list = propertyImageService.list(property.id());

        // then
        assertThat(list).hasSize(1);
        assertThat(uploaded.imageType()).isEqualTo(ImageType.PHOTO);
        final Path original = Paths.get(System.getProperty("java.io.tmpdir"), "halley-test-uploads",
                String.valueOf(property.id()), Paths.get(uploaded.storagePath()).getFileName().toString());
        assertThat(Files.exists(original)).isTrue();
        assertThat(original.getFileName().toString()).contains("_original.jpg");
    }

    private PropertyRequest request() {
        return new PropertyRequest(
                "사진 테스트", null, DealType.SALE, 500_000_000L, null, null,
                "서울시", null, null, null,
                null, null, null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private byte[] jpegBytes() throws Exception {
        final BufferedImage image = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
