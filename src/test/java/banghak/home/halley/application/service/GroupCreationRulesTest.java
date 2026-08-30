package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.exception.DuplicateGroupNameException;
import banghak.home.halley.config.exception.TooManyEmptyGroupsException;
import banghak.home.halley.domain.user.UserRole;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("그룹 생성 규칙 (설계 I104)")
class GroupCreationRulesTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("같은 이름의 그룹은 만들 수 없다 — 회원을 넣을 때 어느 쪽인지 가릴 수 없다")
    void rejectsDuplicateName() {
        // given
        final String name = "겹치는이름" + SEQ.incrementAndGet();
        fillEmptySlots();
        groupService.createByAdmin(name);
        // 방금 만든 그룹에 회원을 넣어 빈 그룹 자리를 비운다
        userService.create(new CreateUserRequest(
                "dup-" + name, "회원-" + name,
                userGroupRepository.findByName(name).orElseThrow().id(),
                "password1!", UserRole.MEMBER, null, null, null, 0L, 0L, 0L));

        // when · then
        assertThatThrownBy(() -> groupService.createByAdmin(name))
                .isInstanceOf(DuplicateGroupNameException.class);
    }

    @Test
    @DisplayName("이름을 비우면 무작위 한국어로 지어진다")
    void generatesNameWhenBlank() {
        fillEmptySlots();

        final var created = groupService.createByAdmin("  ");

        assertThat(created.name()).isNotBlank();
        assertThat(created.memberCount()).isZero();
    }

    @Test
    @DisplayName("회원이 없는 그룹은 2개까지 — 버튼을 누를 때마다 쌓이면 목록이 못 쓰게 된다")
    void limitsEmptyGroups() {
        // given — 빈 그룹을 정확히 2개로 맞춘다
        fillEmptySlots();
        groupService.createByAdmin("빈그룹A" + SEQ.incrementAndGet());
        groupService.createByAdmin("빈그룹B" + SEQ.incrementAndGet());

        // when · then
        assertThatThrownBy(() -> groupService.createByAdmin("빈그룹C" + SEQ.incrementAndGet()))
                .isInstanceOf(TooManyEmptyGroupsException.class);
    }

    /**
     * 같은 스프링 컨텍스트를 공유해 다른 테스트가 만든 빈 그룹이 남아 있다.
     * 회원을 넣어 빈 그룹 수를 0으로 만든 뒤 시작한다.
     */
    private void fillEmptySlots() {
        userGroupRepository.findAll().stream()
                .filter(g -> userRepository.findByGroupId(g.id()).isEmpty())
                .forEach(g -> {
                    final int n = SEQ.incrementAndGet();
                    userService.create(new CreateUserRequest(
                            "filler-" + n, "채움이-" + n, g.id(), "password1!",
                            UserRole.MEMBER, null, null, null, 0L, 0L, 0L));
                });
    }
}
