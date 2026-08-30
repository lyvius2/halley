package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyImageResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.ImageType;
import org.junit.jupiter.api.DisplayName;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "app.images.dir=${java.io.tmpdir}/halley-test-uploads")
class PropertyImageServiceTest {

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository groupTestUserRepository;

    /** 매물은 그룹에 딸리므로 그룹에 속한 회원으로 로그인해 둔다 (설계 I87). */
    @BeforeEach
    void loginAsGroupMember() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, groupTestUserRepository);
    }

    @AfterEach
    void clearLogin() {
        GroupTestSupport.logout();
    }

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

    @Test
    @DisplayName("평면도는 매물당 한 장 — 다시 올리면 기존 것을 대체한다 (설계 I63)")
    void floorPlanIsReplacedNotStacked() throws Exception {
        // given
        final PropertyResponse property = propertyService.create(request());
        final PropertyImageResponse first = propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "plan1.jpg", "image/jpeg", jpegBytes()), ImageType.FLOOR_PLAN);

        // when — 평면도를 다시 올린다
        final PropertyImageResponse second = propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "plan2.jpg", "image/jpeg", jpegBytes()), ImageType.FLOOR_PLAN);

        // then — 도면이 쌓이지 않고 대체된다. 여러 장 남으면 어느 것이 맞는지 알 수 없다
        final List<PropertyImageResponse> list = propertyImageService.list(property.id());
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().id()).isEqualTo(second.id());
        // 옛 평면도의 파일도 지워진다
        assertThat(Files.exists(pathOf(property.id(), first))).isFalse();
        assertThat(Files.exists(pathOf(property.id(), second))).isTrue();
    }

    @Test
    @DisplayName("매물사진은 여러 장 쌓이고 평면도가 목록 맨 앞에 온다")
    void photosStackAndFloorPlanComesFirst() throws Exception {
        // given
        final PropertyResponse property = propertyService.create(request());
        propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes()), ImageType.PHOTO);
        propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "b.jpg", "image/jpeg", jpegBytes()), ImageType.PHOTO);

        // when — 사진을 먼저 올린 뒤 평면도를 올린다
        propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "plan.jpg", "image/jpeg", jpegBytes()), ImageType.FLOOR_PLAN);

        // then
        final List<PropertyImageResponse> list = propertyImageService.list(property.id());
        assertThat(list).hasSize(3);
        assertThat(list.getFirst().imageType()).isEqualTo(ImageType.FLOOR_PLAN);
        assertThat(list.subList(1, 3)).allMatch(i -> i.imageType() == ImageType.PHOTO);
    }

    @Test
    @DisplayName("사진을 지우면 레코드와 파일이 함께 사라진다")
    void deleteRemovesRecordAndFiles() throws Exception {
        // given
        final PropertyResponse property = propertyService.create(request());
        final PropertyImageResponse uploaded = propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes()), ImageType.PHOTO);

        // when
        propertyImageService.delete(property.id(), uploaded.id());

        // then
        assertThat(propertyImageService.list(property.id())).isEmpty();
        assertThat(Files.exists(pathOf(property.id(), uploaded))).isFalse();
    }

    @Test
    @DisplayName("다른 매물의 이미지는 지울 수 없다")
    void cannotDeleteAnotherPropertysImage() throws Exception {
        // given
        final PropertyResponse mine = propertyService.create(request());
        final PropertyResponse other = propertyService.create(request());
        final PropertyImageResponse uploaded = propertyImageService.upload(other.id(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes()), ImageType.PHOTO);

        // when · then
        assertThatThrownBy(() -> propertyImageService.delete(mine.id(), uploaded.id()))
                .isInstanceOf(NotFoundListingsException.class);
        assertThat(propertyImageService.list(other.id())).hasSize(1);
    }

    @Test
    @DisplayName("종류를 지정하지 않으면 거부한다")
    void requiresImageType() throws Exception {
        final PropertyResponse property = propertyService.create(request());
        assertThatThrownBy(() -> propertyImageService.upload(property.id(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes()), null))
                .isInstanceOf(InvalidPropertyRequestException.class);
    }

    private Path pathOf(Long propertyId, PropertyImageResponse image) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "halley-test-uploads",
                String.valueOf(propertyId), Paths.get(image.storagePath()).getFileName().toString());
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
