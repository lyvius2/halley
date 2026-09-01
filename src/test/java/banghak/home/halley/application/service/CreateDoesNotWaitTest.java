package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.cache.InMemoryCachePort;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록은 보정을 <b>기다리지 않는다</b> (설계 I220).
 *
 * <p>[I110]은 초등학교·토지이용계획·채점까지 기다렸다가 돌려줬습니다. 그때는
 * 그게 맞았지만, ODsay 가 막혀 직주근접이 LLM 으로 넘어가면(I210) 사람당 4~5초라
 * <b>등록 한 번이 수십 초</b>가 됩니다.
 *
 * <p>이 테스트가 지키는 것은 <b>두 가지</b>입니다.
 * <ol>
 *   <li>보정 중인 매물을 목록·상세가 만나도 <b>그 자리에서 채점하지 않는다</b> —
 *       안 그러면 기다림이 목록으로 옮겨 갔을 뿐이다</li>
 *   <li>표시가 없으면 <b>평소대로 계산한다</b> — 옛 매물이 스스로 낫는 길(I84)을 막지 않는다</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("등록은 보정을 기다리지 않는다 (설계 I220)")
class CreateDoesNotWaitTest {

    /** 이 테스트는 채점이 <b>비켜서는지</b>만 본다 — 실제 보정은 다른 테스트의 몫이다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private PropertyService propertyService;
    @Autowired private ScoringService scoringService;
    @Autowired private CachePort cache;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void login() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        ((InMemoryCachePort) cache).evictAll(CachePort.ENRICHING);
    }

    @AfterEach
    void logout() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("보정 중이면 채점하지 않고 '아직'이라고 답한다 — 목록이 그만큼 멈추면 안 된다")
    void stepsAsideWhileEnriching() {
        final Long id = propertyService.create(request("보정중매물")).id();
        cache.put(CachePort.ENRICHING, String.valueOf(id), "1", java.time.Duration.ofMinutes(5));

        final var scored = scoringService.getScored(id);

        assertThat(scored.scores()).isEmpty();
        assertThat(scored.totalScore()).isNull();
        // 매물 자체는 멀쩡히 실려 온다 — 카드가 보여야 한다
        assertThat(scored.property().name()).isEqualTo("보정중매물");
    }

    @Test
    @DisplayName("표시가 없으면 평소대로 계산한다 — 스스로 낫는 길을 막지 않는다")
    void scoresNormallyWithoutTheMarker() {
        final Long id = propertyService.create(request("평소매물")).id();

        final var scored = scoringService.getScored(id);

        assertThat(scored.scores()).isNotEmpty();
    }

    @Test
    @DisplayName("보정이 끝나 표시가 걷히면 그다음 조회부터 점수가 나온다")
    void scoresOnceTheMarkerIsGone() {
        final Long id = propertyService.create(request("끝난매물")).id();
        cache.put(CachePort.ENRICHING, String.valueOf(id), "1", java.time.Duration.ofMinutes(5));
        assertThat(scoringService.getScored(id).scores()).isEmpty();

        cache.evict(CachePort.ENRICHING, String.valueOf(id));

        assertThat(scoringService.getScored(id).scores()).isNotEmpty();
    }

    @Test
    @DisplayName("이미 채점된 매물은 표시가 있어도 그 점수를 보여 준다 — 있는 것을 숨길 이유가 없다")
    void alreadyScoredIsNotHidden() {
        final Long id = propertyService.create(request("채점된매물")).id();
        assertThat(scoringService.getScored(id).scores()).isNotEmpty();

        cache.put(CachePort.ENRICHING, String.valueOf(id), "1", java.time.Duration.ofMinutes(5));

        assertThat(scoringService.getScored(id).scores()).isNotEmpty();
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
