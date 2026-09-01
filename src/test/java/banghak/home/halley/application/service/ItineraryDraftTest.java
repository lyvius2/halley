package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ItineraryDraft;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.UserRole;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임장 작업 상태는 계정마다 다르다 (설계 I179).
 *
 * <p>운영에서 <b>A가 계산한 결과가 B에게 그대로 보였습니다.</b> 화면 상태로만 두었기
 * 때문인데, 이제 서버가 <b>사용자별로</b> 담습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("임장 작업 상태 (설계 I179)")
class ItineraryDraftTest {

    @Autowired
    private ItineraryService itineraryService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    private Long groupId;

    @BeforeEach
    void setUp() {
        groupId = GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        itineraryService.clearDraft();
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("담아 두고 그대로 꺼낸다")
    void roundTrip() {
        itineraryService.saveDraft(new ItineraryDraft(List.of(3L, 1L, 2L), "TRANSIT", null));

        final ItineraryDraft loaded = itineraryService.loadDraft();
        assertThat(loaded.propertyIds()).containsExactly(3L, 1L, 2L);
        assertThat(loaded.travelMode()).isEqualTo("TRANSIT");
    }

    /** <b>이 테스트가 이 기능의 이유입니다.</b> */
    @Test
    @DisplayName("다른 계정에는 안 보인다 — A가 짜던 동선이 B에게 보이면 안 된다")
    void notVisibleToAnotherAccount() {
        itineraryService.saveDraft(new ItineraryDraft(List.of(1L, 2L), "DRIVING", null));
        assertThat(itineraryService.loadDraft().propertyIds()).hasSize(2);

        // 같은 그룹의 다른 사람으로 바꿔 앉는다
        GroupTestSupport.login(anotherMember());

        assertThat(itineraryService.loadDraft().propertyIds()).isEmpty();
    }

    @Test
    @DisplayName("담은 적 없으면 빈 것으로 시작한다 — null 이 아니다")
    void emptyWhenNeverSaved() {
        itineraryService.clearDraft();

        final ItineraryDraft draft = itineraryService.loadDraft();
        assertThat(draft).isNotNull();
        assertThat(draft.propertyIds()).isEmpty();
        assertThat(draft.result()).isNull();
    }

    @Test
    @DisplayName("지우면 사라진다 — 로그아웃할 때 부른다")
    void clears() {
        itineraryService.saveDraft(new ItineraryDraft(List.of(1L), "DRIVING", null));

        itineraryService.clearDraft();

        assertThat(itineraryService.loadDraft().propertyIds()).isEmpty();
    }

    private User anotherMember() {
        final long n = System.nanoTime();
        return userRepository.save(new User(
                null, "itin" + n, "임장다른이" + n, groupId, "hash", UserRole.MEMBER,
                null, null, null, false, false, 0L, 0L, 0L, true, null, null, Instant.now()));
    }
}
