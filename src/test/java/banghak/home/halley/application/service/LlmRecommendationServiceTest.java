package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.LlmRecommendationRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmRecommendation;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LLM 추천도 (설계 I59)")
class LlmRecommendationServiceTest {

    private final LlmRecommendationRepository recommendationRepository = mock(LlmRecommendationRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("JSON 응답에서 점수와 이유를 뽑아 저장한다")
    void storesScoreAndReason() {
        // given
        givenPropertyAndUsers();
        final LlmRecommendationService service = service(stub(LlmResult.of(
                "{\"score\": 82, \"reason\": \"직장까지 가깝고 세대수가 충분합니다.\"}", "claude-x")));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.ensureRecommendation(1L);

        // then
        final ArgumentCaptor<LlmRecommendation> captor = ArgumentCaptor.forClass(LlmRecommendation.class);
        verify(recommendationRepository).upsert(captor.capture());
        assertThat(captor.getValue().score()).isEqualByComparingTo("82.00");
        assertThat(captor.getValue().reason()).isEqualTo("직장까지 가깝고 세대수가 충분합니다.");
        assertThat(captor.getValue().model()).isEqualTo("claude-x");
        assertThat(captor.getValue().promptHash()).isNotBlank();
    }

    @Test
    @DisplayName("모델이 코드펜스나 설명을 덧붙여도 JSON만 잘라 읽는다")
    void toleratesSurroundingProse() {
        // given
        givenPropertyAndUsers();
        final LlmRecommendationService service = service(stub(LlmResult.of("""
                판단 결과입니다.
                ```json
                {"score": 45, "reason": "역이 멀고 연식이 오래됐습니다."}
                ```
                """, "claude-x")));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.ensureRecommendation(1L);

        // then
        final ArgumentCaptor<LlmRecommendation> captor = ArgumentCaptor.forClass(LlmRecommendation.class);
        verify(recommendationRepository).upsert(captor.capture());
        assertThat(captor.getValue().score()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("입력이 그대로면 다시 부르지 않는다 — 같은 답을 다시 사지 않는다")
    void skipsCallWhenPromptUnchanged() {
        // given
        givenPropertyAndUsers();
        final AtomicInteger calls = new AtomicInteger();
        final LlmPort port = countingPort(calls, LlmResult.of("{\"score\": 70, \"reason\": \"무난\"}", "m"));
        final LlmRecommendationService service = service(port);
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // 첫 호출로 해시를 얻는다
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        service.ensureRecommendation(1L);
        final ArgumentCaptor<LlmRecommendation> captor = ArgumentCaptor.forClass(LlmRecommendation.class);
        verify(recommendationRepository).upsert(captor.capture());
        final String hash = captor.getValue().promptHash();

        // when — 같은 해시가 저장돼 있는 상태로 다시 부른다
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.of(new LlmRecommendation(
                9L, 1L, new BigDecimal("70.00"), "무난", "m", hash, 2, Instant.now())));
        final Optional<LlmRecommendation> second = service.ensureRecommendation(1L);

        // then — LLM은 한 번만 불렸다
        assertThat(calls.get()).isEqualTo(1);
        assertThat(second).isPresent();
    }

    @Test
    @DisplayName("파싱할 수 없는 응답이면 저장하지 않고 기존 값을 유지한다")
    void keepsPreviousWhenUnparseable() {
        // given
        givenPropertyAndUsers();
        final LlmRecommendation previous = new LlmRecommendation(
                9L, 1L, new BigDecimal("70.00"), "이전 판단", "m", "old-hash", 2, Instant.now());
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.of(previous));
        final LlmRecommendationService service = service(stub(LlmResult.of("잘 모르겠습니다", "m")));

        // when
        final Optional<LlmRecommendation> result = service.ensureRecommendation(1L);

        // then
        verify(recommendationRepository, never()).upsert(any());
        assertThat(result).contains(previous);
    }

    @Test
    @DisplayName("범위를 벗어난 점수는 받아들이지 않는다")
    void rejectsOutOfRangeScore() {
        // given
        givenPropertyAndUsers();
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        final LlmRecommendationService service = service(stub(LlmResult.of("{\"score\": 140}", "m")));

        // when
        service.ensureRecommendation(1L);

        // then
        verify(recommendationRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("LLM이 꺼져 있으면 호출하지 않고 저장된 값만 돌려준다")
    void doesNotCallWhenDisabled() {
        // given
        final AtomicInteger calls = new AtomicInteger();
        final LlmPort port = countingPort(calls, LlmResult.of("{\"score\": 70}", "m"));
        final LlmRecommendationService service = new LlmRecommendationService(
                port, recommendationRepository, propertyRepository, userRepository, objectMapper, false);
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());

        // when
        service.ensureRecommendation(1L);

        // then
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("프롬프트에 매물 정보와 구매자들의 직장 위치가 함께 담긴다")
    void promptCarriesPropertyAndWorkplaces() {
        // given
        givenPropertyAndUsers();
        final AtomicReference<LlmMessage> sent = new AtomicReference<>();
        final LlmPort port = new LlmPort() {
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
                sent.set(message);
                return LlmResult.of("{\"score\": 60, \"reason\": \"보통\"}", "m");
            }
        };
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service(port).ensureRecommendation(1L);

        // then
        final String prompt = sent.get().user();
        assertThat(prompt).contains("단지명: 테스트단지");
        assertThat(prompt).contains("매매가/보증금(원): 800000000");
        assertThat(prompt).contains("[구매자들의 직장 위치]");
        assertThat(prompt).contains("앨리스: 강남역");
        assertThat(prompt).contains("밥: 판교역");
        // 값이 없는 항목은 지어내지 않고 '정보 없음'으로 남긴다
        assertThat(prompt).contains("공시가격(원): 정보 없음");
    }

    @Test
    @DisplayName("직장 위치가 3곳 이상으로 이미 추론된 매물은 사용자가 늘어도 다시 묻지 않는다")
    void skipsWhenThreeWorkplacesAlreadyUsed() {
        // given — 3곳으로 추론해 둔 매물
        when(propertyRepository.findAll()).thenReturn(List.of(property()));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.of(new LlmRecommendation(
                9L, 1L, new BigDecimal("70.00"), "이미 충분", "m", "old-hash", 3, Instant.now())));
        final AtomicInteger calls = new AtomicInteger();
        final LlmRecommendationService service =
                service(countingPort(calls, LlmResult.of("{\"score\": 90}", "m")));

        // when — 네 번째 사람이 들어와 직장 위치가 바뀐 상황
        final int refreshed = service.refreshForWorkplaceChange();

        // then — LLM을 부르지 않는다
        assertThat(calls.get()).isZero();
        assertThat(refreshed).isZero();
        verify(recommendationRepository, never()).upsert(any());
    }

    @Test
    @DisplayName("직장 위치가 2곳뿐이면 사용자가 추가될 때 다시 묻는다")
    void refreshesWhenFewerThanThreeWorkplaces() {
        // given — 2곳으로만 추론해 둔 매물
        givenPropertyAndUsers();
        when(propertyRepository.findAll()).thenReturn(List.of(property()));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.of(new LlmRecommendation(
                9L, 1L, new BigDecimal("70.00"), "아직 부족", "m", "old-hash", 2, Instant.now())));
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final AtomicInteger calls = new AtomicInteger();
        final LlmRecommendationService service = service(countingPort(
                calls, LlmResult.of("{\"score\": 90, \"reason\": \"직장이 더 가까워졌습니다\"}", "m")));

        // when
        final int refreshed = service.refreshForWorkplaceChange();

        // then
        assertThat(calls.get()).isEqualTo(1);
        assertThat(refreshed).isEqualTo(1);
    }

    @Test
    @DisplayName("아직 추론된 적 없는 매물은 직장 위치 수와 무관하게 뽑는다")
    void refreshesWhenNeverComputed() {
        // given
        givenPropertyAndUsers();
        when(propertyRepository.findAll()).thenReturn(List.of(property()));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));
        final AtomicInteger calls = new AtomicInteger();
        final LlmRecommendationService service =
                service(countingPort(calls, LlmResult.of("{\"score\": 65, \"reason\": \"보통\"}", "m")));

        // when
        service.refreshForWorkplaceChange();

        // then
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("추론에 쓰인 직장 위치 수를 함께 저장한다 — 다음에 건너뛸지 판단하는 근거")
    void storesWorkplaceCount() {
        // given — 좌표가 있는 사용자 2명
        givenPropertyAndUsers();
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service(stub(LlmResult.of("{\"score\": 80, \"reason\": \"좋음\"}", "m"))).ensureRecommendation(1L);

        // then
        final ArgumentCaptor<LlmRecommendation> captor = ArgumentCaptor.forClass(LlmRecommendation.class);
        verify(recommendationRepository).upsert(captor.capture());
        assertThat(captor.getValue().workplaceCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("좌표 없는 사용자는 직장 위치 수에 세지 않는다")
    void countsOnlyUsersWithCoordinates() {
        // given — 2명 중 1명만 좌표가 있다
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property()));
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "앨리스", "강남역"),
                new User(2L, "login2", "밥", "b@example.com", "hash", UserRole.MEMBER,
                        "판교역", null, null, false, 300_000_000L, 60_000_000L, 0L,
                        true, null, null, Instant.now())));
        when(recommendationRepository.findByPropertyId(1L)).thenReturn(Optional.empty());
        when(recommendationRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        service(stub(LlmResult.of("{\"score\": 80, \"reason\": \"좋음\"}", "m"))).ensureRecommendation(1L);

        // then
        final ArgumentCaptor<LlmRecommendation> captor = ArgumentCaptor.forClass(LlmRecommendation.class);
        verify(recommendationRepository).upsert(captor.capture());
        assertThat(captor.getValue().workplaceCount()).isEqualTo(1);
    }

    private LlmRecommendationService service(LlmPort port) {
        return new LlmRecommendationService(
                port, recommendationRepository, propertyRepository, userRepository, objectMapper, true);
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

    private void givenPropertyAndUsers() {
        when(propertyRepository.findById(1L)).thenReturn(Optional.of(property()));
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "앨리스", "강남역"),
                user(2L, "밥", "판교역")));
    }

    private User user(Long id, String nickname, String workplace) {
        return new User(id, "login" + id, nickname, nickname + "@example.com", "hash", UserRole.MEMBER,
                workplace, new BigDecimal("37.5"), new BigDecimal("127.0"),
                false, 300_000_000L, 60_000_000L, 0L, true, null, null, Instant.now());
    }

    private Property property() {
        return new Property(
                1L, "테스트단지", "102동", DealType.SALE, 800_000_000L, null, 200_000,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                new BigDecimal("110.0"), new BigDecimal("84.9"), null, 14, 20, null, "3/2", "남향",
                1995, null, null, new BigDecimal("1.2"), 436, "개별난방", null, null,
                null, null, null, null, null,
                "서울혜화초등학교", 6, null, null, null, null,
                SourceType.PASTE, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, 1L, Instant.now());
    }
}
