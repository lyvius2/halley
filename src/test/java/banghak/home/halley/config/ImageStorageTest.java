package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사진 파일의 자리 (설계 I202 · I205).
 *
 * <p>스프링 없이 그냥 만듭니다 — 경로 계산만 보는 것이라 컨텍스트가 필요 없습니다.
 */
@DisplayName("사진 저장 위치 (설계 I202·I205)")
class ImageStorageTest {

    private final ImageStorage storage = new ImageStorage("/tmp/halley-images");

    @Test
    @DisplayName("평범한 파일 이름은 매물 폴더 안으로 풀린다")
    void normalNameResolvesInsideTheFolder() {
        final Path file = storage.fileIn(12L, "PHOTO_ab12cd34_original.jpg");

        assertThat(file).isNotNull();
        assertThat(file).isEqualTo(Paths.get("/tmp/halley-images/12/PHOTO_ab12cd34_original.jpg"));
    }

    /**
     * <b>`..` 는 이름 검사를 통과합니다.</b> 점 둘 다 허용 글자라 정규식으로는 안 걸립니다.
     *
     * <p>여기를 잡는 것은 뒤의 "폴더 안인가" 검사입니다 — 이 한 줄이 없으면
     * <b>부모 디렉터리</b>가 그대로 돌아옵니다. 두 검사 중 어느 하나만 두면 안 되는
     * 이유가 이것입니다.
     */
    @Test
    @DisplayName("'..' 는 이름 검사를 통과한다 — 폴더 밖인지 한 번 더 봐야 잡힌다")
    void dotDotEscapesTheNameCheckButNotTheFolderCheck() {
        assertThat("..".matches("[A-Za-z0-9_.-]{1,120}")).isTrue();

        assertThat(storage.fileIn(12L, "..")).isNull();
    }

    @Test
    @DisplayName("하위 경로는 이름 검사에서 끝난다 — 폴더 안이라 뒤 검사로는 못 잡는다")
    void nestedPathIsRejectedByTheNameCheck() {
        assertThat(storage.fileIn(12L, "sub/secret.jpg")).isNull();
    }

    @Test
    @DisplayName("경로 탈출 이름은 거절한다")
    void traversalIsRejected() {
        assertThat(storage.fileIn(12L, "../../etc/passwd")).isNull();
        assertThat(storage.fileIn(12L, "../13/PHOTO_x_original.jpg")).isNull();
        assertThat(storage.fileIn(12L, null)).isNull();
        assertThat(storage.fileIn(12L, "")).isNull();
    }

    /**
     * 상대 경로는 <b>JVM 을 띄운 디렉터리</b> 기준입니다 (설계 I202) — jar 가 놓인 자리가
     * 아닙니다. 다른 디렉터리에서 다시 띄우면 DB 기록은 남고 파일만 안 보입니다.
     */
    @Test
    @DisplayName("상대 경로는 실행 디렉터리 기준으로 풀린다")
    void relativeDirResolvesAgainstTheWorkingDirectory() {
        final Path file = new ImageStorage("uploads").fileIn(12L, "a.jpg");

        assertThat(file).isEqualTo(
                Paths.get(System.getProperty("user.dir"), "uploads", "12", "a.jpg"));
    }
}
