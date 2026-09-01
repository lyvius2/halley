package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.application.service.PropertyAccessGuard;
import banghak.home.halley.config.ImageStorage;
import banghak.home.halley.config.exception.NotFoundListingsException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 올린 사진을 내려 준다 — <b>그룹 경계를 지나서</b> (설계 I205).
 *
 * <p>전에는 정적 리소스 처리기가 `/uploads/**` 를 그대로 열어 줬습니다. 시큐리티 설정의
 * `anyRequest().permitAll()` 에 걸려 <b>로그인 없이도 URL 만 알면 열렸고</b>, 로그인해도
 * <b>남의 그룹 사진</b>을 볼 수 있었습니다. 파일 이름의 무작위 8글자가 유일한 방어였는데,
 * 그건 접근 제어가 아니라 <b>추측하기 어렵다는 것</b>뿐입니다.
 *
 * <p>[I87]이 매물을 읽는 스무 곳을 한 길목으로 모았는데 <b>사진만 그 밖에 있었습니다.</b>
 * 이제 여기도 `PropertyAccessGuard` 를 지납니다.
 *
 * <p><b>주소는 그대로 둡니다</b>(`/uploads/{매물id}/{파일명}`). 이미 저장된
 * `property_image.storage_path` 가 그 꼴이라, 바꾸면 옛 사진이 전부 깨집니다.
 */
@RestController
public class UploadedImageController {

    /**
     * 파일은 <b>바뀌지 않습니다</b> — 이름에 무작위 8글자가 박혀 있고, 고치면 새 이름이
     * 생깁니다. 그래서 오래 캐시해도 됩니다.
     *
     * <p><b>`private` 입니다.</b> 그룹마다 다른 자료라 중간 캐시가 들고 있으면 안 됩니다 —
     * 방금 인증으로 막아 둔 것이 캐시로 새어 나갑니다.
     */
    private static final CacheControl CACHE =
            CacheControl.maxAge(Duration.ofDays(30)).cachePrivate();

    private final PropertyAccessGuard propertyAccessGuard;
    private final ImageStorage imageStorage;

    public UploadedImageController(PropertyAccessGuard propertyAccessGuard, ImageStorage imageStorage) {
        this.propertyAccessGuard = propertyAccessGuard;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/uploads/{propertyId}/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable Long propertyId, @PathVariable String fileName) {
        // 볼 수 없는 매물이면 여기서 404 — 사진이 있는지조차 알려 주지 않는다 (설계 I87)
        propertyAccessGuard.require(propertyId);
        final Path file = imageStorage.fileIn(propertyId, fileName);
        if (file == null || !Files.isRegularFile(file)) {
            throw new NotFoundListingsException();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CACHE)
                .body(new FileSystemResource(file));
    }
}
