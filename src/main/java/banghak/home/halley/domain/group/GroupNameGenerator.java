package banghak.home.halley.domain.group;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 그룹 이름을 무작위 한국어로 짓는다 (설계 I87 · 규칙 14).
 *
 * <p>회원가입하면 그룹이 자동으로 생기는데 <b>이름을 물어보지 않습니다.</b> 가입 순간에는
 * 그룹이 무엇인지도 모르는 상태라, 이름을 요구하면 의미 없는 값이 들어갑니다. 나중에 그룹의
 * 누구나 바꿀 수 있습니다.
 *
 * <p>집을 고르는 앱이므로 <b>집·자리에 관한 말</b>로 짓습니다.
 */
public final class GroupNameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "포근한", "볕드는", "조용한", "다정한", "산뜻한", "너그러운", "느긋한",
            "말끔한", "정겨운", "고요한", "환한", "선선한", "아늑한", "든든한");

    private static final List<String> NOUNS = List.of(
            "보금자리", "둥지", "우리집", "터전", "마루", "사랑방", "뜨락",
            "쉼터", "온돌방", "다락", "정원", "골목", "창가", "현관");

    /** @return `포근한 보금자리` 같은 이름 */
    public static String generate() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                + " " + NOUNS.get(random.nextInt(NOUNS.size()));
    }

    private GroupNameGenerator() {
    }
}
