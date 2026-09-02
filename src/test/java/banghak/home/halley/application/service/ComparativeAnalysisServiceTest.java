package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.ComparativeAnalysisRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.adapter.outbound.cache.InMemoryLlmJobCache;
import banghak.home.halley.application.port.out.cache.LlmJobCache;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.config.exception.InsufficientPropertiesException;
import banghak.home.halley.config.exception.LlmUnavailableException;
import banghak.home.halley.domain.llm.ComparativeAnalysis;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("비교 우위 분석 (설계 I61)")
class ComparativeAnalysisServiceTest {

    private final ComparativeAnalysisRepository analysisRepository = mock(ComparativeAnalysisRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ScoringService scoringService = mock(ScoringService.class);
    private final PropertyAccessGuard accessGuard = mock(PropertyAccessGuard.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("매물이 4개 미만이면 실행하지 않는다 — 비교 우위라는 말이 성립하지 않는다")
    void requiresAtLeastFourProperties() {
        // given
        givenProperties(3);
        final AtomicInteger calls = new AtomicInteger();
        final ComparativeAnalysisService service = service(countingPort(calls, LlmResult.of("{}", "m")));

        // when · then
        assertThatThrownBy(service::analyse)
                .isInstanceOf(InsufficientPropertiesException.class)
                .hasMessageContaining("최소 4개")
                .hasMessageContaining("현재 3개");
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("4개 이상이면 순위·점수·이유를 매물별로 저장한다")
    void storesRankingsForFourProperties() {
        // given
        givenProperties(4);
        when(analysisRepository.findAll()).thenReturn(List.of());
        when(analysisRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final ComparativeAnalysisService service = service(stub(LlmResult.of("""
                {"rankings": [
                  {"propertyId": 2, "rank": 1, "score": 91, "reason": "1번보다 역이 가깝습니다"},
                  {"propertyId": 1, "rank": 2, "score": 74, "reason": "면적은 넓지만 연식이 오래됐습니다"},
                  {"propertyId": 4, "rank": 3, "score": 60, "reason": "가격은 싸지만 통근이 멉니다"},
                  {"propertyId": 3, "rank": 4, "score": 41, "reason": "세대수가 적습니다"}]}
                """, "claude-x")));

        // when
        final List<ComparativeAnalysis> result = service.analyse();

        // then — 1위부터 정렬돼 돌아온다
        assertThat(result).extracting(ComparativeAnalysis::propertyId).containsExactly(2L, 1L, 4L, 3L);
        assertThat(result.getFirst().score()).isEqualByComparingTo("91.00");
        assertThat(result.getFirst().rankNo()).isEqualTo(1);
        assertThat(result.getFirst().propertyCount()).isEqualTo(4);
        assertThat(result.getFirst().reason()).contains("1번보다");
        // 점수가 바뀌었으니 전 매물을 다시 채점한다
        verify(scoringService).rescoreAll();
    }

    @Test
    @DisplayName("일부 매물만 순위가 매겨지면 결과 전체를 버린다 — '몇 개 중 몇 위'가 거짓이 된다")
    void discardsPartialRankings() {
        // given — 4개 중 3개만 답했다
        givenProperties(4);
        when(analysisRepository.findAll()).thenReturn(List.of());
        final ComparativeAnalysisService service = service(stub(LlmResult.of("""
                {"rankings": [
                  {"propertyId": 1, "rank": 1, "score": 90, "reason": "a"},
                  {"propertyId": 2, "rank": 2, "score": 80, "reason": "b"},
                  {"propertyId": 3, "rank": 3, "score": 70, "reason": "c"}]}
                """, "m")));

        // when · then
        assertThatThrownBy(service::analyse).isInstanceOf(LlmUnavailableException.class);
        verify(analysisRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("매물 집합이 그대로면 다시 부르지 않는다")
    void skipsWhenBatchUnchanged() {
        // given — 첫 분석으로 해시를 얻는다
        givenProperties(4);
        when(analysisRepository.findAll()).thenReturn(List.of());
        when(analysisRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final AtomicInteger calls = new AtomicInteger();
        final ComparativeAnalysisService service = service(countingPort(calls, LlmResult.of("""
                {"rankings": [
                  {"propertyId": 1, "rank": 1, "score": 90, "reason": "a"},
                  {"propertyId": 2, "rank": 2, "score": 80, "reason": "b"},
                  {"propertyId": 3, "rank": 3, "score": 70, "reason": "c"},
                  {"propertyId": 4, "rank": 4, "score": 60, "reason": "d"}]}
                """, "m")));
        service.analyse();
        final ArgumentCaptor<ComparativeAnalysis> captor = ArgumentCaptor.forClass(ComparativeAnalysis.class);
        verify(analysisRepository, org.mockito.Mockito.atLeastOnce()).upsert(captor.capture());
        final String hash = captor.getValue().batchHash();

        // when — 같은 해시가 4건 저장된 상태로 다시 부른다
        final List<ComparativeAnalysis> cached = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            cached.add(new ComparativeAnalysis(id, id, (int) id, new BigDecimal("70.00"),
                    "이유", "m", hash, 4, Instant.now()));
        }
        when(analysisRepository.findAll()).thenReturn(cached);
        service.analyse();

        // then — LLM은 한 번만 불렸다
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("판매완료·초안은 비교 대상에서 뺀다 — 살 수 없는 집과 견주면 순위가 왜곡된다")
    void excludesSoldOutAndDrafts() {
        // given — 활성 4개 + 판매완료 1개 + 초안 1개
        final List<Property> all = new ArrayList<>(properties(4));
        all.add(property(5L, "판매완료", false, false));
        all.add(property(6L, "작성 중", true, true));
        when(accessGuard.currentGroupId()).thenReturn(java.util.Optional.of(GROUP_ID));
        when(propertyRepository.findByGroupId(GROUP_ID)).thenReturn(all);
        givenUsers();
        when(analysisRepository.findAll()).thenReturn(List.of());
        when(analysisRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final AtomicReference<LlmMessage> sent = new AtomicReference<>();
        final ComparativeAnalysisService service = service(capturingPort(sent, LlmResult.of("""
                {"rankings": [
                  {"propertyId": 1, "rank": 1, "score": 90, "reason": "a"},
                  {"propertyId": 2, "rank": 2, "score": 80, "reason": "b"},
                  {"propertyId": 3, "rank": 3, "score": 70, "reason": "c"},
                  {"propertyId": 4, "rank": 4, "score": 60, "reason": "d"}]}
                """, "m")));

        // when
        service.analyse();

        // then
        assertThat(sent.get().user()).contains("[비교 대상 매물 4건]");
        assertThat(sent.get().user()).doesNotContain("propertyId=5");
        assertThat(sent.get().user()).doesNotContain("propertyId=6");
    }

    @Test
    @DisplayName("현황은 매물 수와 실행 가능 여부를 알려준다")
    void reportsStatus() {
        // given
        givenProperties(3);
        when(analysisRepository.findAll()).thenReturn(List.of());
        final ComparativeAnalysisService service = service(stub(LlmResult.of("{}", "m")));

        // when
        final var status = service.status();

        // then
        assertThat(status.analysable()).isFalse();
        assertThat(status.propertyCount()).isEqualTo(3);
        assertThat(status.minProperties()).isEqualTo(4);
    }

    @Test
    @DisplayName("LLM이 꺼져 있으면 실행할 수 없다")
    void requiresLlm() {
        // given
        givenProperties(4);
        final ComparativeAnalysisService service = new ComparativeAnalysisService(
                stub(LlmResult.of("{}", "m")), llmModels(), analysisRepository, jobCache, propertyRepository,
                userRepository, accessGuard, scoringService, objectMapper, false);

        // when · then
        assertThatThrownBy(service::analyse).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    @DisplayName("프롬프트에 매물별 propertyId와 구매자 직장 위치가 담긴다")
    void promptCarriesIdsAndWorkplaces() {
        // given
        givenProperties(4);
        when(analysisRepository.findAll()).thenReturn(List.of());
        when(analysisRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final AtomicReference<LlmMessage> sent = new AtomicReference<>();
        service(capturingPort(sent, LlmResult.of("""
                {"rankings": [
                  {"propertyId": 1, "rank": 1, "score": 90, "reason": "a"},
                  {"propertyId": 2, "rank": 2, "score": 80, "reason": "b"},
                  {"propertyId": 3, "rank": 3, "score": 70, "reason": "c"},
                  {"propertyId": 4, "rank": 4, "score": 60, "reason": "d"}]}
                """, "m"))).analyse();

        // then
        final String prompt = sent.get().user();
        assertThat(prompt).contains("## propertyId=1").contains("## propertyId=4");
        assertThat(prompt).contains("[구매자들의 직장 위치]").contains("앨리스: 강남역");
    }

    private final LlmJobCache jobCache = new InMemoryLlmJobCache();

    private ComparativeAnalysisService service(LlmPort port) {
        return new ComparativeAnalysisService(port, llmModels(), analysisRepository, jobCache, propertyRepository,
                userRepository, accessGuard, scoringService, objectMapper, true);
    }

    private LlmPort stub(LlmResult result) {
        return countingPort(new AtomicInteger(), result);
    }

    private LlmPort countingPort(AtomicInteger calls, LlmResult result) {
        return new LlmPort() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public LlmResult complete(LlmMessage message) {
                calls.incrementAndGet();
                return result;
            }
        };
    }

    private LlmPort capturingPort(AtomicReference<LlmMessage> sink, LlmResult result) {
        return new LlmPort() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public LlmResult complete(LlmMessage message) {
                sink.set(message);
                return result;
            }
        };
    }

    /** 비교 대상은 내 그룹 매물만이다 (설계 I91). 로그인한 그룹을 정해 둔다. */
    private static final Long GROUP_ID = 7L;

    private void givenProperties(int count) {
        when(accessGuard.currentGroupId()).thenReturn(java.util.Optional.of(GROUP_ID));
        when(propertyRepository.findByGroupId(GROUP_ID)).thenReturn(properties(count));
        givenUsers();
    }

    private void givenUsers() {
        when(userRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                new User(1L, "login1", "앨리스", GROUP_ID, "hash", UserRole.MEMBER,
                        "강남역", new BigDecimal("37.49"), new BigDecimal("127.02"),
                        false, false, 300_000_000L, 60_000_000L, 0L, true, null, null, Instant.now())));
    }

    private List<Property> properties(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> property((long) i, "매물" + i, true, false))
                .toList();
    }

    private Property property(Long id, String name, boolean active, boolean draft) {
        return new Property(
                id, name, "10" + id + "동", DealType.SALE, 500_000_000L + id * 10_000_000L, 200_000,
                null, "서울시 어딘가 " + id, new BigDecimal("37.5"), new BigDecimal("127.0"),
                new BigDecimal("110.0"), new BigDecimal("84.9"), null, 5, 20, null, "3/2", "남향",
                2010, null, null, new BigDecimal("1.2"), 400, "개별난방", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                draft, active ? ListingStatus.ACTIVE : ListingStatus.SOLD_OUT, active,
                null, 0, null, GROUP_ID, "테스터", 1L, Instant.now());
    }

    /**
     * 자리마다 모델을 고르는 설정 (설계 I267) — 이 시험은 <b>모델을 안 고른</b> 상태로 본다.
     *
     * <p>{@code null} 이면 어댑터가 기본 모델을 씁니다. 여기서 특정 이름을 박아 두면
     * 시험이 <b>설정이 아니라 그 이름</b>을 재게 됩니다.
     */
    private static LlmModelService llmModels() {
        final LlmModelService models = org.mockito.Mockito.mock(LlmModelService.class);
        org.mockito.Mockito.when(models.modelFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        return models;
    }
}
