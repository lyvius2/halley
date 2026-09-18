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

@RestController
public class UploadedImageController {

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
        // 볼 수 없는 매물이면 여기서 404 — 사진이 있는지조차 알려 주지 않는다
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
