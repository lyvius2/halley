package banghak.home.halley.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 올린 사진이 실제로 놓이는 곳.
 *
 * 경로를 두 곳에서 각자 풀고 있었습니다(업로드하는 쪽과 내려 주는 쪽). 한쪽만 고치면
 * 올라간 자리와 찾는 자리가 갈라지는데, 그때 나는 증상은 "사진이 안 보인다"뿐입니다.
 *
 * `app.images.dir` 이 상대 경로면 JVM 을 띄운 디렉터리 기준입니다 —
 * jar 가 놓인 자리가 아닙니다. 다른 디렉터리에서 다시 띄우면 DB 기록은 남고 파일만
 * 안 보입니다. 그래서 시작할 때 실제 절대 경로를 로그에 찍습니다.
 */
@Slf4j
@Component
public class ImageStorage {

    /**
     * 파일 이름으로 받아들일 글자.
     *
     * 여기가 경로 탈출을 막는 자리입니다. `/` 도 `.` 둘도 통과하지 못합니다 —
     * `../../etc/passwd` 같은 이름이 오면 이름 검사에서 끝납니다.
     */
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

    /**
     * 한 파일의 자리.
     *
     * 이름을 먼저 거르고, 그러고도 풀린 경로가 매물 폴더 안인지 한 번 더 봅니다.
     * 둘 다 필요합니다 — 서로 못 잡는 것이 하나씩 있습니다:
     *
     *
     * sub/secret.jpg  이름 검사만 잡는다 — 폴더 안이라 아래 검사는 통과시킨다
     * ..              폴더 검사만 잡는다 — 점 둘 다 허용 글자라 정규식을 통과한다
     *
     *
     * @return 그 파일. 이름이 수상하거나 폴더 밖으로 나가면 null
     */
    public Path fileIn(Long propertyId, String fileName) {
        if (fileName == null || !fileName.matches(SAFE_NAME)) {
            return null;
        }
        final Path dir = dirOf(propertyId);
        final Path resolved = dir.resolve(fileName).normalize();
        return resolved.startsWith(dir) ? resolved : null;
    }
}
