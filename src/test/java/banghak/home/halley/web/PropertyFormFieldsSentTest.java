package banghak.home.halley.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 폼에 있는 칸이 서버로 가고 있는가 (설계 I269) — [I265]에서 이 누락을 겪었다. */
@DisplayName("폼의 칸이 요청에 실리는가 (설계 I269)")
class PropertyFormFieldsSentTest {

    private static final Path SCRIPT = Path.of("src/main/resources/static/js/app.js");

    /**
     * 보내지 <b>않는 것</b>이 맞는 칸.
     *
     * <p>{@code id} 는 URL 로 가고, {@code editVersion} 은 헤더로 가며,
     * {@code carry} 는 <b>폼에 칸이 없는 값</b>을 그대로 되돌려보내는 자리입니다 ([I113]).
     */
    private static final Set<String> NOT_SENT = Set.of("id", "editVersion", "carry");

    @Test
    @DisplayName("emptyPropertyForm 의 칸은 모두 saveProperty 가 담는다")
    void everyFormFieldIsSent() throws IOException {
        final String script = Files.readString(SCRIPT);

        final List<String> formFields = keysOf(
                block(script, "function emptyPropertyForm\\(\\)\\s*\\{\\s*return \\{", "\\n    \\};"), 8);
        final Set<String> sent = new LinkedHashSet<>(keysOf(
                block(script, "async saveProperty\\(\\)\\s*\\{", "\\n            try \\{"), 16));

        // 검사가 실제로 무언가를 보고 있는가 — 0건이면 통과가 뜻이 없다
        assertThat(formFields).as("emptyPropertyForm 을 못 읽었다면 정규식이 어긋난 것이다")
                .hasSizeGreaterThan(10);
        assertThat(sent).as("saveProperty 를 못 읽었다면 정규식이 어긋난 것이다")
                .hasSizeGreaterThan(10);

        final List<String> missing = formFields.stream()
                .filter(field -> !NOT_SENT.contains(field))
                .filter(field -> !sent.contains(field))
                .toList();
        assertThat(missing)
                .as("폼에는 칸이 있는데 요청에 안 실린다 — 고쳐도 안 바뀌고, 아무 데서도 안 걸린다")
                .isEmpty();
    }

    @Test
    @DisplayName("붙여넣기 등록도 동/호를 담는다")
    void pasteRequestCarriesTheBuilding() throws IOException {
        final String script = Files.readString(SCRIPT);
        final Set<String> sent = new LinkedHashSet<>(keysOf(
                block(script, "buildPasteRequest\\(\\)\\s*\\{", "\\n        \\},"), 16));

        assertThat(sent).as("buildPasteRequest 를 못 읽었다면 정규식이 어긋난 것이다")
                .hasSizeGreaterThan(10);
        assertThat(sent)
                .as("파서가 읽고 화면이 보여 준 값을 안 보내면 DB 는 늘 비어 있다 (설계 I265)")
                .contains("dongHo");
    }

    /** {@code start} 부터 {@code end} 앞까지. */
    private static String block(String source, String start, String end) {
        final Matcher matcher = Pattern.compile(start + "(.*?)" + end, Pattern.DOTALL).matcher(source);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 들여쓰기 {@code indent} 칸에 놓인 {@code 이름:} 들. */
    private static List<String> keysOf(String block, int indent) {
        final Matcher matcher = Pattern.compile(
                "^ {" + indent + "}([a-zA-Z_$][\\w$]*)\\s*:", Pattern.MULTILINE).matcher(block);
        final List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
