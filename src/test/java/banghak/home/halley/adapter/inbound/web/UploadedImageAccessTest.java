package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.service.PropertyEnrichmentService;
import banghak.home.halley.application.service.PropertyImageService;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.config.ImageStorage;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ImageType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 올린 사진에도 그룹 경계가 있다 (설계 I205).
 *
 * <p>정적 리소스로 열어 두는 바람에 <b>로그인 없이 URL 만 알면</b> 열렸고, 로그인해도
 * <b>남의 그룹 사진</b>을 볼 수 있었습니다. 파일 이름의 무작위 8글자가 유일한 방어였는데
 * 그건 접근 제어가 아닙니다.
 *
 * <p><b>두 각도로 봅니다.</b> 인증 여부는 시큐리티 설정의 몫이라 MockMvc 로,
 * 그룹 경계와 경로 탈출은 컨트롤러의 몫이라 직접 불러서 봅니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "app.images.dir=build/test-uploads")
@DisplayName("올린 사진 접근 (설계 I205)")
class UploadedImageAccessTest {

    /** 이 테스트는 접근 제어만 본다 — 보정이 같은 매물에 트랜잭션을 잡으면 엉뚱하게 느려진다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadedImageController controller;
    @Autowired private PropertyService propertyService;
    @Autowired private PropertyImageService propertyImageService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ImageStorage imageStorage;

    private Long propertyId;
    private String fileName;

    @BeforeEach
    void uploadOnePhoto() throws Exception {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        propertyId = propertyService.create(new PropertyRequest(
                "사진매물", null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
        final String storagePath = propertyImageService.upload(
                propertyId, new MockMultipartFile("file", "a.jpg", "image/jpeg", jpegBytes()),
                ImageType.PHOTO).storagePath();
        fileName = Paths.get(storagePath).getFileName().toString();
    }

    @AfterEach
    void clearAuth() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("로그인하지 않으면 사진도 못 본다 — 전에는 URL 만 알면 열렸다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/uploads/" + propertyId + "/" + fileName))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정적 리소스로 새어 나가지 않는다 — 처리기를 걷어냈다")
    void noStaticResourceFallback() throws Exception {
        // 없는 파일이라도 401 이어야 한다. 200 이나 404 면 컨트롤러 앞에서
        // 다른 무언가가 /uploads 를 잡고 있다는 뜻이다
        mockMvc.perform(get("/uploads/" + propertyId + "/nope.jpg"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 그룹 사진은 내려온다")
    void ownGroupPhotoIsServed() {
        final ResponseEntity<Resource> response = controller.image(propertyId, fileName);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    /**
     * 중간 캐시가 들고 있으면 <b>방금 인증으로 막은 것이 캐시로 새어 나갑니다.</b>
     * 그룹마다 다른 자료라 `public` 이면 안 됩니다.
     */
    @Test
    @DisplayName("캐시는 private 이다 — 그룹마다 다른 자료다")
    void cacheIsPrivate() {
        final String cacheControl = controller.image(propertyId, fileName)
                .getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);

        assertThat(cacheControl).contains("private");
        assertThat(cacheControl).doesNotContain("public");
    }

    @Test
    @DisplayName("남의 그룹 사진은 없는 것으로 친다 — 로그인만으로는 부족하다")
    void otherGroupPhotoIsNotFound() {
        // 다른 그룹 사람으로 갈아탄다. 매물 번호도 파일명도 아는 상태다
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);

        assertThatThrownBy(() -> controller.image(propertyId, fileName))
                .isInstanceOf(NotFoundListingsException.class);
    }

    /**
     * 파일명은 우리가 지은 값이지만 <b>주소는 누구나 짓습니다.</b>
     * 여기가 뚫리면 서버의 아무 파일이나 나갑니다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "..",
            "../a.jpg",
            "sub/dir.jpg",
            "a.jpg .png"
    })
    @DisplayName("폴더 밖으로 나가는 이름은 거절한다")
    void pathTraversalIsRejected(String name) {
        assertThatThrownBy(() -> controller.image(propertyId, name))
                .isInstanceOf(NotFoundListingsException.class);
    }

    /**
     * 이름 검사가 <b>혼자서도 서는지</b> (설계 I205).
     *
     * <p>`../` 는 뒤에 있는 "폴더 안인가" 검사가 잡아 줍니다. 그래서 이름 검사를 통째로
     * 지워도 앞의 경로 탈출 테스트들은 <b>그대로 통과합니다</b> — 실제로 겪었습니다.
     *
     * <p>이름 검사만 막을 수 있는 것은 <b>폴더 안에 머무는 하위 경로</b>입니다.
     * 진짜 파일을 하나 만들어 두고 확인합니다. 안 그러면 "파일이 없어서 404"인지
     * "거절해서 404"인지 구분되지 않습니다.
     */
    @Test
    @DisplayName("폴더 안이어도 하위 경로는 거절한다 — 이름 검사가 혼자 서야 한다")
    void nestedPathIsRejectedEvenWhenTheFileExists() throws Exception {
        final Path nested = imageStorage.dirOf(propertyId).resolve("sub");
        Files.createDirectories(nested);
        Files.write(nested.resolve("secret.jpg"), jpegBytes());

        assertThatThrownBy(() -> controller.image(propertyId, "sub/secret.jpg"))
                .isInstanceOf(NotFoundListingsException.class);
    }

    @Test
    @DisplayName("없는 파일은 404 — 있는지조차 알려 주지 않는다")
    void missingFileIsNotFound() {
        assertThatThrownBy(() -> controller.image(propertyId, "PHOTO_deadbeef_original.jpg"))
                .isInstanceOf(NotFoundListingsException.class);
    }

    /** 진짜 JPEG 여야 한다 — Thumbnails 가 읽지 못하면 업로드부터 실패한다. */
    private static byte[] jpegBytes() throws Exception {
        final BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
