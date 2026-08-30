package banghak.home.halley.support;

import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 그룹에 속한 회원으로 로그인한 상태를 만든다 (설계 I87).
 *
 * <p>매물은 <b>반드시 그룹에 딸리므로</b> 등록하려면 그룹에 속한 회원이 있어야 합니다.
 * 인증이 없으면 등록 자체가 막히는데, 그건 격리가 제대로 걸렸다는 뜻이기도 합니다.
 */
public final class GroupTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** @return 만들어진 그룹의 id */
    public static Long loginAsGroupMember(UserGroupRepository groups, UserRepository users) {
        final int n = SEQ.incrementAndGet();
        final UserGroup group = groups.save(new UserGroup(null, "테스트그룹" + n, null, null, Instant.now()));
        final User saved = users.save(new User(
                null, "tester" + n, "테스터" + n, group.id(), "hash", UserRole.MEMBER,
                null, null, null, false, 0L, 0L, 0L, true, null, null, Instant.now()));
        login(saved);
        return group.id();
    }

    public static void login(User user) {
        final HalleyUserDetails details = new HalleyUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }

    private GroupTestSupport() {
    }
}
