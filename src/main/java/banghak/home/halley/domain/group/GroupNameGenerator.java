package banghak.home.halley.domain.group;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 그룹 이름을 무작위 한국어로 짓는다. */
public final class GroupNameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "포근한", "볕드는", "조용한", "다정한", "산뜻한", "너그러운", "느긋한",
            "말끔한", "정겨운", "고요한", "환한", "선선한", "아늑한", "든든한");

    private static final List<String> NOUNS = List.of(
            "보금자리", "둥지", "우리집", "터전", "마루", "사랑방", "뜨락",
            "쉼터", "온돌방", "다락", "정원", "골목", "창가", "현관");


    public static String generate() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                + " " + NOUNS.get(random.nextInt(NOUNS.size()));
    }

    private GroupNameGenerator() {
    }
}
