package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.GroupInviteResponse;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.WithdrawRequest;
import banghak.home.halley.adapter.outbound.persistence.GroupInviteRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.exception.AlreadyInGroupException;
import banghak.home.halley.config.exception.InviteExpiredException;
import banghak.home.halley.config.exception.InviteNotFoundException;
import banghak.home.halley.domain.group.GroupInvite;
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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("그룹 초대·가입·탈퇴 (설계 I89)")
class GroupServiceTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private GroupInviteRepository inviteRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @AfterEach
    void clearAuth() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("초대 코드는 숫자·영문 대소문자 8자리다")
    void inviteCodeShape() {
        // given
        login(member("inv" + SEQ.incrementAndGet()));

        // when
        final GroupInviteResponse invite = groupService.createInvite();

        // then
        assertThat(invite.code()).hasSize(8).matches("[0-9A-Za-z]{8}");
        assertThat(invite.expiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("초대에 응하면 그룹이 바뀌고 코드는 한 번 쓰면 사라진다")
    void joinMovesGroupAndConsumesCode() {
        // given — 초대하는 쪽과 받는 쪽
        final String tag = "join" + SEQ.incrementAndGet();
        final Long hostId = member(tag + "-host");
        login(hostId);
        final String code = groupService.createInvite().code();
        final Long hostGroup = userRepository.findById(hostId).orElseThrow().groupId();

        final Long guestId = member(tag + "-guest");
        login(guestId);

        // when
        groupService.joinByInvite(code);

        // then
        assertThat(userRepository.findById(guestId).orElseThrow().groupId()).isEqualTo(hostGroup);
        // 남겨 두면 24시간 동안 누구나 더 들어올 수 있다
        assertThat(inviteRepository.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("마지막 한 사람이 나가면 그룹과 그 매물이 함께 사라진다")
    void emptyGroupIsRemovedWithProperties() {
        // given — 혼자 있는 그룹에 매물 하나
        final String tag = "empty" + SEQ.incrementAndGet();
        final Long loneId = member(tag + "-lone");
        login(loneId);
        final Long leftGroup = userRepository.findById(loneId).orElseThrow().groupId();
        final Long propertyId = propertyService.create(request("떠나며 남긴 매물")).id();

        // 다른 그룹에서 초대가 온다
        final Long hostId = member(tag + "-host");
        login(hostId);
        final String code = groupService.createInvite().code();

        // when — 초대에 응해 옮겨 간다
        login(loneId);
        groupService.joinByInvite(code);

        // then — 아무도 볼 수 없는 자료를 남기지 않는다 (규칙 4)
        assertThat(userGroupRepository.findById(leftGroup)).isEmpty();
        assertThat(propertyRepository.findById(propertyId)).isEmpty();
    }

    @Test
    @DisplayName("만료된 코드는 받아 주지 않고 치운다")
    void expiredInviteRejected() {
        // given — 이미 지난 코드
        final String tag = "exp" + SEQ.incrementAndGet();
        final Long hostId = member(tag + "-host");
        final Long hostGroup = userRepository.findById(hostId).orElseThrow().groupId();
        final String code = "Expired9";
        inviteRepository.saveIfAbsent(new GroupInvite(
                code, hostGroup, hostId,
                Instant.now().minus(25, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)));
        login(member(tag + "-guest"));

        // when · then
        assertThatThrownBy(() -> groupService.joinByInvite(code))
                .isInstanceOf(InviteExpiredException.class);

        // 만료된 코드는 다음 발급 때 치운다 — 남겨 두면 같은 문자열을 다시 쓸 수 없다
        login(hostId);
        groupService.createInvite();
        assertThat(inviteRepository.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("없는 코드와 이미 속한 그룹의 코드는 거절한다")
    void rejectsUnknownAndSelfInvite() {
        // given
        final String tag = "self" + SEQ.incrementAndGet();
        login(member(tag));
        final String ownCode = groupService.createInvite().code();

        // when · then
        assertThatThrownBy(() -> groupService.joinByInvite("nope1234"))
                .isInstanceOf(InviteNotFoundException.class);
        assertThatThrownBy(() -> groupService.joinByInvite(ownCode))
                .isInstanceOf(AlreadyInGroupException.class);
    }

    @Test
    @DisplayName("탈퇴해도 남은 사람이 있으면 매물은 남고 등록자 이름이 보인다")
    void withdrawalKeepsPropertiesWhenGroupSurvives() {
        // given — 한 그룹에 둘, 그중 하나가 매물을 올렸다
        final String tag = "wd" + SEQ.incrementAndGet();
        final Long aliceId = member(tag + "-alice");
        final Long aliceGroup = userRepository.findById(aliceId).orElseThrow().groupId();
        login(aliceId);
        final String code = groupService.createInvite().code();
        final Long bobId = member(tag + "-bob");
        login(bobId);
        groupService.joinByInvite(code);

        login(aliceId);
        final Long propertyId = propertyService.create(request("앨리스가 올린 매물")).id();

        // when — 앨리스가 탈퇴한다
        userService.withdraw(new WithdrawRequest("password1!"));

        // then — 회원은 사라지지만 매물은 남고, 이름은 값으로 남아 있다 (규칙 15·16)
        assertThat(userRepository.findById(aliceId)).isEmpty();
        assertThat(userGroupRepository.findById(aliceGroup)).isPresent();
        assertThat(propertyRepository.findById(propertyId)).isPresent()
                .get()
                .satisfies(p -> assertThat(p.createdByNickname()).isEqualTo("회원-" + tag + "-alice"));
    }

    private Long member(String tag) {
        return userService.create(new CreateUserRequest(
                tag, "회원-" + tag, null, "password1!", UserRole.MEMBER,
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
