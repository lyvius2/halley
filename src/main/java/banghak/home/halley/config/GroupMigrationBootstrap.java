package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 그룹이 없던 시절의 회원·매물을 기본 그룹으로 옮긴다 (설계 I87).
 *
 * <p><b>이게 없으면 기동 즉시 아무도 자기 매물을 못 봅니다.</b> 매물은 그룹으로 걸러 보여
 * 주는데 기존 자료에는 그룹이 없기 때문입니다. 회원 관점에서는 자료가 사라진 것과 같습니다.
 *
 * <p>한 번만 돕니다 — 그룹이 하나라도 있으면 이미 옮긴 것으로 봅니다. admin은 어느 그룹에도
 * 넣지 않습니다(규칙 5).
 */
@Slf4j
@Component
@Order(5)
public class GroupMigrationBootstrap implements ApplicationRunner {

    private static final String DEFAULT_GROUP_NAME = "우리 집 찾기";

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    public GroupMigrationBootstrap(UserGroupRepository userGroupRepository,
                                   UserRepository userRepository,
                                   PropertyRepository propertyRepository) {
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        final List<User> orphans = userRepository.findAll().stream()
                .filter(u -> u.role() != UserRole.ADMIN)
                .filter(u -> u.groupId() == null)
                .toList();
        final List<Property> ungrouped = propertyRepository.findAll().stream()
                .filter(p -> p.groupId() == null)
                .toList();
        if (orphans.isEmpty() && ungrouped.isEmpty()) {
            return;
        }
        final UserGroup group = userGroupRepository.save(
                new UserGroup(null, DEFAULT_GROUP_NAME, null, Instant.now()));
        orphans.forEach(u -> userRepository.update(u.withGroupId(group.id())));
        ungrouped.forEach(p -> propertyRepository.update(p.withGroupId(group.id())));
        log.info("Migrated pre-group data. groupId={}, users={}, properties={}",
                group.id(), orphans.size(), ungrouped.size());
    }
}
