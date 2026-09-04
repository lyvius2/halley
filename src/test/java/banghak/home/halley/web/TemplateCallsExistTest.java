package banghak.home.halley.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>화면이 없는 함수를 부르고 있지 않은가</b> (설계 I264).
 *
 * <p>빌드 단계가 없어 이런 실수가 <b>브라우저에서만</b> 드러납니다. 실제로
 * {@code scoredComfort} 가 그랬습니다 — 매물 카드마다 콘솔에 빨간 줄이 났고,
 * 배지 색은 늘 남의 것으로 보였는데 <b>아무 테스트도 안 깨졌습니다.</b>
 *
 * <p>Alpine 식은 문자열이라 컴파일러가 안 봅니다. 여기서 대신 봅니다.
 */
@DisplayName("화면이 부르는 함수가 실제로 있는가 (설계 I264)")
class TemplateCallsExistTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/index.mustache");
    private static final Path SCRIPT = Path.of("src/main/resources/static/js/app.js");

    /** Alpine 식이 실리는 자리 — {@code x-…} · {@code :…} · {@code @…}. */
    private static final Pattern EXPRESSION =
            Pattern.compile("(?:x-[\\w:.-]+|:[\\w:.-]+|@[\\w:.-]+)=\"([^\"]*)\"");
    /** {@code foo(} — 앞에 점이나 글자가 없어야 한다. {@code a.foo()} 는 우리 것이 아니다. */
    private static final Pattern CALL =
            Pattern.compile("(?<![.\\w$])([a-z_$][\\w$]*)\\s*\\(");
    /** Alpine 객체의 메서드 — 들여쓰기 여덟 칸이 이 파일의 약속이다. */
    private static final Pattern METHOD =
            Pattern.compile("^\\s{8}(?:async\\s+)?([a-zA-Z_$][\\w$]*)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern TOP_LEVEL =
            Pattern.compile("^\\s*(?:function|const|let|var)\\s+([a-zA-Z_$][\\w$]*)", Pattern.MULTILINE);

    /** 자바스크립트가 원래 아는 것들. 우리가 정의할 대상이 아니다. */
    private static final Set<String> BUILT_IN = Set.of(
            "if", "for", "while", "switch", "catch", "return", "typeof", "new", "function",
            "in", "of", "do", "else", "await", "delete", "void", "instanceof",
            "String", "Number", "Boolean", "Array", "Object", "JSON", "Math", "Date",
            "parseInt", "parseFloat", "isNaN", "isFinite",
            "encodeURIComponent", "decodeURIComponent", "alert", "confirm", "prompt");

    @Test
    @DisplayName("템플릿이 부르는 함수는 모두 app.js 에 있다")
    void everyCalledFunctionExists() throws IOException {
        final String template = Files.readString(TEMPLATE);
        final String script = Files.readString(SCRIPT);

        final Set<String> defined = new LinkedHashSet<>();
        collect(METHOD, script, defined);
        collect(TOP_LEVEL, script, defined);

        final Set<String> missing = new LinkedHashSet<>();
        final Matcher expressions = EXPRESSION.matcher(template);
        while (expressions.find()) {
            final Matcher calls = CALL.matcher(expressions.group(1));
            while (calls.find()) {
                final String name = calls.group(1);
                // $nextTick · $refs 같은 Alpine 매직은 우리가 정의하지 않는다
                if (name.startsWith("$") || BUILT_IN.contains(name) || defined.contains(name)) {
                    continue;
                }
                missing.add(name);
            }
        }

        // 검사가 실제로 무언가를 보고 있는지부터 — 0건이면 통과가 뜻이 없다
        assertThat(defined).as("app.js 에서 함수를 하나도 못 찾았다면 정규식이 어긋난 것이다")
                .hasSizeGreaterThan(100);
        assertThat(missing)
                .as("화면이 부르는데 app.js 에 없는 함수 — 브라우저 콘솔에만 뜬다")
                .isEmpty();
    }

    private static void collect(Pattern pattern, String source, Set<String> into) {
        final Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }
}
