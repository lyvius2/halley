package banghak.home.halley.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 올린 사진이 실제로 놓이는 곳. */
@Slf4j
@Component
public class ImageStorage {

 /** 파일 이름으로 받아들일 글자. */
    private static final String SAFE_NAME = "[A-Za-z0-9_.-]{1,120}";

    private final String configured;
    private final Path root;

    public ImageStorage(@Value("${app.images.dir:uploads}") String configured) {
        this.configured = configured;
        this.root = Paths.get(configured).toAbsolutePath().normalize();
    }

    @PostConstruct
    void announce() {
        if (!Paths.get(configured).isAbsolute()) {
            log.warn("app.images.dir is relative ('{}') - it resolves against the working directory, "
                            + "not the jar. Set APP_IMAGES_DIR to an absolute path so photos survive "
                            + "a restart elsewhere. resolved={}",
                    configured, root);
        }
        log.info("Uploaded images live in {} (exists={}, writable={})",
                root, Files.exists(root), Files.isWritable(root));
    }

    public Path dirOf(Long propertyId) {
        return root.resolve(String.valueOf(propertyId));
    }

 /** 한 파일의 자리. */
    public Path fileIn(Long propertyId, String fileName) {
        if (fileName == null || !fileName.matches(SAFE_NAME)) {
            return null;
        }
        final Path dir = dirOf(propertyId);
        final Path resolved = dir.resolve(fileName).normalize();
        return resolved.startsWith(dir) ? resolved : null;
    }
}
