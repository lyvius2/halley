package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.exception.AdminCannotOwnPropertyException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.user.UserRole;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 그룹 격리 (설계 I87).
 *
 * <p>이 앱에서 <b>가장 조용히 깨질 수 있는 규칙</b>입니다. 매물을 읽는 자리가 스무 곳이
 * 넘는데 한 곳만 그룹 확인을 빠뜨려도 남의 자료가 새고, 새고 있다는 사실은 어디에도
 * 드러나지 않습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("그룹 격리 (설계 I87)")
class GroupIsolationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private ComparativeAnalysisService comparativeAnalysisService;

    @AfterEach
    void clearAuth() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("다른 그룹의 매물은 목록에 뜨지 않는다")
    void listShowsOnlyOwnGroup() {
        // given — 서로 다른 그룹의 두 회원이 각자 매물을 등록한다
        final String tag = "iso" + SEQ.incrementAndGet();
        loginAsNewMember(tag + "-a");
        propertyService.create(request("A그룹 매물"));
        loginAsNewMember(tag + "-b");
        final Long bProperty = propertyService.create(request("B그룹 매물")).id();

        // when — B가 목록을 본다
        final List<ScoredPropertyResponse> visible = scoringService.list(null);

        // then
        assertThat(visible).extracting(r -> r.property().name())
                .contains("B그룹 매물")
                .doesNotContain("A그룹 매물");
        assertThat(visible).extracting(r -> r.property().id()).contains(bProperty);
    }

    @Test
    @DisplayName("다른 그룹의 매물은 번호를 알아도 열리지 않는다 — 403이 아니라 404다")
    void detailHidesOtherGroupProperty() {
        // given
        final String tag = "iso" + SEQ.incrementAndGet();
        loginAsNewMember(tag + "-owner");
        final Long hidden = propertyService.create(request("남의 매물")).id();
        loginAsNewMember(tag + "-stranger");

        // when · then — 없는 것처럼 답한다. 403은 그 번호가 존재한다는 사실을 알려 준다
        assertThatThrownBy(() -> propertyService.get(hidden))
                .isInstanceOf(NotFoundListingsException.class);
        assertThatThrownBy(() -> scoringService.getScored(hidden))
                .isInstanceOf(NotFoundListingsException.class);
    }

    @Test
    @DisplayName("같은 그룹의 매물은 등록자가 아니어도 보인다")
    void sameGroupSharesProperties() {
        // given — 한 그룹에 두 회원
        final String tag = "iso" + SEQ.incrementAndGet();
        final Long groupId = userGroupRepository.save(
                new UserGroup(null, "동거그룹" + tag, null, Instant.now())).id();
        login(createMember(tag + "-1", groupId));
        final Long shared = propertyService.create(request("함께 보는 매물")).id();
        login(createMember(tag + "-2", groupId));

        // when · then
        assertThat(propertyService.get(shared).name()).isEqualTo("함께 보는 매물");
    }

    @Test
    @DisplayName("admin은 모든 그룹의 매물을 보지만 등록은 할 수 없다")
    void adminSeesAllButCannotOwn() {
        // given
        final String tag = "iso" + SEQ.incrementAndGet();
        loginAsNewMember(tag + "-member");
        propertyService.create(request("어느 그룹 매물 " + tag));

        // when — admin으로 바꾼다
        login(userService.create(new CreateUserRequest(
                "admin-" + tag, "관리자-" + tag, null, "password1!", UserRole.ADMIN,
                null, null, null, 0L, 0L, 0L)).id());

        // then — 다 보이지만
        assertThat(scoringService.list(null)).extracting(r -> r.property().name())
                .contains("어느 그룹 매물 " + tag);
        // 등록은 막힌다. 그룹 없는 매물이 생기면 아무도 볼 수 없고 그룹이 사라져도 남는다
        assertThatThrownBy(() -> propertyService.create(request("admin 매물")))
                .isInstanceOf(AdminCannotOwnPropertyException.class);
    }

    @Test
    @DisplayName("그룹 badge는 admin에게만 실려 온다 — 회원은 다른 그룹이 있는지도 몰라야 한다")
    void groupBadgeOnlyForAdmin() {
        // given
        final String tag = "badge" + SEQ.incrementAndGet();
        loginAsNewMember(tag + "-member");
        final Long propertyId = propertyService.create(request("badge 매물 " + tag)).id();

        // when · then — 회원에게는 그룹 이름이 오지 않는다
        assertThat(scoringService.getScored(propertyId).property().groupName()).isNull();

        // admin에게는 온다
        login(userService.create(new CreateUserRequest(
                "badge-admin-" + tag, "관리자-" + tag, null, "password1!", UserRole.ADMIN,
                null, null, null, 0L, 0L, 0L)).id());
        assertThat(scoringService.getScored(propertyId).property().groupName()).isNotBlank();
    }

    @Test
    @DisplayName("가격 점수는 우리 그룹의 현금만 센다 — 남의 그룹 현금이 섞이면 안 된다")
    void cashBudgetCountsOnlyOwnGroup() {
        // given — 다른 그룹에 현금이 아주 많은 사람이 있다
        final String tag = "cash" + SEQ.incrementAndGet();
        userService.create(new CreateUserRequest(
                "rich-" + tag, "부자-" + tag, null, "password1!", UserRole.MEMBER,
                null, null, null, 90_000_000_000L, 60_000_000L, 0L));

        // 우리 그룹에는 현금이 없다
        final Long groupId = userGroupRepository.save(
                new UserGroup(null, "빈털터리" + tag, null, Instant.now())).id();
        login(userService.create(new CreateUserRequest(
                "poor-" + tag, "빈손-" + tag, groupId, "password1!", UserRole.MEMBER,
                null, null, null, 0L, 60_000_000L, 0L)).id());
        final Long propertyId = propertyService.create(request("비싼 집 " + tag)).id();

        // when
        final var price = scoringService.getScored(propertyId).scores().stream()
                .filter(s -> "PRICE".equals(s.code())).findFirst().orElseThrow();

        // then — 900억이 섞였다면 만점이 나온다
        assertThat(price.effectiveScore()).isNotEqualTo(new java.math.BigDecimal("100.00"));
    }

    @Test
    @DisplayName("비교 우위 분석은 우리 그룹 매물만 견준다")
    void comparativeAnalysisStaysInGroup() {
        // given — 다른 그룹에 매물 넷
        final String tag = "cmp" + SEQ.incrementAndGet();
        loginAsNewMember(tag + "-other");
        for (int i = 0; i < 4; i++) {
            propertyService.create(request("남의 매물 " + tag + i));
        }

        // 우리 그룹에는 하나뿐이다
        loginAsNewMember(tag + "-mine");
        propertyService.create(request("내 매물 " + tag));

        // when · then — 남의 넷이 세어졌다면 실행 가능으로 나온다
        assertThat(comparativeAnalysisService.status().propertyCount()).isEqualTo(1);
        assertThat(comparativeAnalysisService.status().analysable()).isFalse();
    }

    private void loginAsNewMember(String tag) {
        login(createMember(tag, null));
    }

    private Long createMember(String tag, Long groupId) {
        return userService.create(new CreateUserRequest(
                tag, "회원-" + tag, groupId, "password1!", UserRole.MEMBER,
                null, null, null, 300_000_000L, 60_000_000L, 0L)).id();
    }

    private void login(Long userId) {
        GroupTestSupport.login(userRepository.findById(userId).orElseThrow());
    }

    private PropertyRequest request(String name) {
        return new PropertyRequest(
                name, null, DealType.SALE, 500_000_000L, null,
                "서울시", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
