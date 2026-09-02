package banghak.home.halley.adapter.outbound.persistence;

import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 끄는 스위치가 <b>실제로 꽂혀 있는가</b> (설계 I242).
 *
 * <p>{@code ReferenceDataCacheToggleTest} 는 클래스를 직접 만들어 봅니다 — 그래서
 * {@code @Value} 의 키를 틀려도 통과합니다. 문서에는 환경변수 이름을 적어 두었는데
 * <b>어디에도 안 꽂혀 있으면</b> 껐다고 생각하며 켠 채로 재게 됩니다.
 *
 * <p>이 프로젝트는 조절할 값을 전부 {@code application.yaml} 에
 * {@code ${ENV_NAME:기본값}} 으로 적어 둡니다. 그래야 <b>grep 으로 잡히고</b>
 * 무엇을 넣을 수 있는지 한 곳에서 보입니다. 이 스위치만 그 규칙 밖에 있었습니다.
 */
@DisplayName("기준 정보 캐시 스위치 배선 (설계 I242)")
class ReferenceDataCacheWiringTest {

    private static final String KEY = "halley.cache.reference.enabled";

    @SpringBootTest
    @ActiveProfiles("local")
    @Nested
    @DisplayName("기본값")
    class ByDefault {

        @Autowired private Environment environment;

        @Test
        @DisplayName("아무것도 안 하면 켜져 있다")
        void isOnUnlessTurnedOff() {
            assertThat(environment.getProperty(KEY, Boolean.class))
                    .as("%s 가 선언돼 있지 않으면 null 이다 — 문서에만 있는 스위치가 된다", KEY)
                    .isTrue();
        }
    }

    @SpringBootTest(properties = KEY + "=false")
    @ActiveProfiles("local")
    @Nested
    @DisplayName("꺼 두면")
    class WhenTurnedOff {

        @Autowired private Environment environment;
        @Autowired private CriterionRepository criterionRepository;
        @Autowired private DSLContext dsl;

        @Test
        @DisplayName("스위치가 앱까지 닿는다")
        void reachesTheApplication() {
            assertThat(environment.getProperty(KEY, Boolean.class)).isFalse();
        }

        /**
         * <b>값이 아니라 쿼리를 봅니다</b> (설계 I242).
         *
         * <p>설정값만 확인하면 {@code @Value} 의 키를 틀려도 통과합니다 — 스위치는
         * 꺼져 있는데 캐시는 켜진 채로 재게 됩니다. 실제로 <b>매번 원본까지
         * 가는지</b>를 봐야 스위치가 꽂혀 있다고 말할 수 있습니다.
         */
        @Test
        @DisplayName("꺼 두면 매번 원본까지 간다")
        void alwaysReachesTheDatabase() {
            criterionRepository.findAll();   // 켜져 있었다면 여기서 담긴다

            final AtomicInteger queries = new AtomicInteger();
            dsl.configuration().set(() -> new ExecuteListener() {
                @Override
                public void executeStart(ExecuteContext ctx) {
                    final String sql = ctx.sql() == null ? "" : ctx.sql().toLowerCase(Locale.ROOT);
                    if (Pattern.compile("\\bfrom\\s+\"?criterion\"?(\\s|$|\\))").matcher(sql).find()) {
                        queries.incrementAndGet();
                    }
                }
            });
            try {
                criterionRepository.findAll();
                criterionRepository.findAll();
            } finally {
                dsl.configuration().set(() -> new ExecuteListener() { });
            }

            assertThat(queries.get())
                    .as("꺼 뒀는데 조회가 안 나가면 스위치가 안 꽂힌 것이다")
                    .isEqualTo(2);
        }

        /**
         * 꺼도 <b>답은 같아야</b> 합니다 — 안 그러면 켠 쪽과 견줄 수 없습니다.
         */
        @Test
        @DisplayName("꺼도 같은 값이 온다")
        void stillAnswersTheSame() {
            final var first = criterionRepository.findAll();
            final var second = criterionRepository.findAll();

            assertThat(first).isNotEmpty().isEqualTo(second);
        }
    }
}
