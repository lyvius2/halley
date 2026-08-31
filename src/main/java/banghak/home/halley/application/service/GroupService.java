package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.GroupInviteResponse;
import banghak.home.halley.adapter.inbound.web.dto.GroupDetailResponse;
import banghak.home.halley.adapter.inbound.web.dto.GroupResponse;
import banghak.home.halley.adapter.outbound.persistence.GroupInviteRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.exception.AlreadyInGroupException;
import banghak.home.halley.config.exception.DuplicateGroupNameException;
import banghak.home.halley.config.exception.TooManyEmptyGroupsException;
import banghak.home.halley.config.exception.GroupNotFoundException;
import banghak.home.halley.config.exception.InviteExpiredException;
import banghak.home.halley.config.exception.InviteNotFoundException;
import banghak.home.halley.config.exception.NoGroupException;
import banghak.home.halley.domain.group.GroupInvite;
import banghak.home.halley.domain.group.GroupNameGenerator;
import banghak.home.halley.domain.group.InviteCodeGenerator;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 그룹 가입·이동·정리 (설계 I89).
 *
 * <p>초대 코드로만 그룹을 옮깁니다(규칙 3·8). 코드 전달은 앱이 하지 않습니다(규칙 10).
 */
@Slf4j
@Service
public class GroupService {

    /** 규칙 9. 하루가 지나면 무효다. */
    private static final Duration INVITE_TTL = Duration.ofHours(24);
    /** 코드가 겹치면 다시 뽑는다. 살아 있는 코드가 많아도 이 횟수면 넉넉하다. */
    private static final int MAX_CODE_ATTEMPTS = 10;
    /** 회원이 없는 그룹 상한 (설계 I104). 빈 그룹은 쓸모가 없고 목록만 어지럽힌다. */
    private static final int MAX_EMPTY_GROUPS = 2;
    private static final int MAX_NAME_ATTEMPTS = 20;

    private final UserGroupRepository userGroupRepository;
    private final GroupInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAccessGuard accessGuard;
    private final NotificationService notificationService;

    public GroupService(UserGroupRepository userGroupRepository,
                        GroupInviteRepository inviteRepository,
                        UserRepository userRepository,
                        PropertyRepository propertyRepository,
                        PropertyAccessGuard accessGuard,
                        NotificationService notificationService) {
        this.userGroupRepository = userGroupRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
    }

    /** 내 그룹. admin은 속한 그룹이 없다. */
    public GroupResponse myGroup() {
        final Long groupId = accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
        return toResponse(userGroupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new));
    }

    /**
     * 그룹 정보 화면용 상세 (설계 I123).
     *
     * <p>구성원과 매물 수까지 한 번에 담습니다. 화면이 값마다 따로 부르면
     * 느린 DB에서 왕복이 그만큼 늘어납니다.
     */
    public GroupDetailResponse myGroupDetail() {
        final Long groupId = accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
        final UserGroup group = userGroupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
        final List<User> members = userRepository.findByGroupId(groupId);
        final long totalCash = members.stream()
                .filter(User::enabled)
                .mapToLong(User::cashOrZero)
                .sum();
        return new GroupDetailResponse(
                group.id(), group.name(), group.slackWebhookUrl(),
                (int) members.stream().filter(User::enabled).count(),
                totalCash,
                propertyRepository.findByGroupId(groupId).size(),
                members.stream()
                        .sorted(java.util.Comparator.comparing(User::nickname))
                        .map(u -> new GroupDetailResponse.Member(
                                u.id(), u.nickname(), u.workplaceName(),
                                u.cashOrZero(), u.enabled()))
                        .toList(),
                group.createdAt());
    }

    /** 그룹 이름은 <b>그 그룹의 누구나</b> 바꾼다 (설계 I87). */
    @Transactional
    public GroupResponse rename(String name) {
        if (name == null || name.isBlank()) {
            throw new GroupNotFoundException();
        }
        final Long groupId = accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
        userGroupRepository.rename(groupId, name.trim());
        return myGroup();
    }

    /**
     * 알림이 나갈 Slack 웹훅을 정한다 (설계 I96).
     *
     * <p>그룹의 누구나 바꿉니다 — 이름 변경과 같은 기준입니다. 비우면 <b>알림이 나가지
     * 않습니다.</b> 전역 주소로 흘려보내면 우리 매물이 남의 채널에 뜹니다.
     */
    @Transactional
    public GroupResponse updateWebhook(String webhookUrl) {
        final Long groupId = accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
        final String trimmed = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim();
        userGroupRepository.updateWebhook(groupId, trimmed);
        return myGroup();
    }

    /**
     * 저장된 웹훅으로 테스트 메시지를 보낸다 (설계 I96).
     *
     * <p>주소를 잘못 넣어도 알림이 조용히 안 갈 뿐이라, <b>넣고 바로 확인할 수 있어야</b> 합니다.
     */
    public boolean testWebhook() {
        final Long groupId = accessGuard.currentGroupId().orElseThrow(NoGroupException::new);
        return userGroupRepository.findById(groupId)
                .filter(UserGroup::hasWebhook)
                .map(g -> notificationService.testSend(g.slackWebhookUrl()))
                .orElse(false);
    }

    /**
     * 내 그룹으로 부를 초대 코드를 만든다 (규칙 3·8·9).
     *
     * <p>코드가 기본키라 <b>겹치면 삽입이 실패합니다.</b> 그때 다시 뽑습니다 — 만들기 전에
     * 있는지 확인하는 방식은 두 사람이 동시에 같은 코드를 뽑는 경우를 놓칩니다.
     */
    @Transactional
    public GroupInviteResponse createInvite() {
        final User me = accessGuard.currentUser().orElseThrow(NoGroupException::new);
        final Long groupId = me.groupId();
        if (groupId == null) {
            throw new NoGroupException();
        }
        final Instant now = Instant.now();
        inviteRepository.deleteExpired(now);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            final GroupInvite invite = new GroupInvite(
                    InviteCodeGenerator.generate(), groupId, me.id(), now, now.plus(INVITE_TTL));
            if (inviteRepository.saveIfAbsent(invite)) {
                log.info("Group invite created. groupId={}, expiresAt={}", groupId, invite.expiresAt());
                return new GroupInviteResponse(invite.code(), invite.expiresAt());
            }
        }
        // 여기까지 오면 난수가 고장 났거나 살아 있는 코드가 비정상적으로 많다
        throw new IllegalStateException("초대 코드를 만들지 못했습니다");
    }

    /**
     * 초대 코드로 그룹을 옮긴다 (규칙 3·11).
     *
     * <p><b>원래 그룹에 아무도 남지 않으면 그 그룹과 매물이 함께 사라집니다</b>(규칙 4).
     * 되돌릴 수 없으므로 화면에서 미리 경고합니다.
     */
    @Transactional
    public GroupResponse joinByInvite(String code) {
        final User me = accessGuard.currentUser().orElseThrow(NoGroupException::new);
        final GroupInvite invite = inviteRepository.findByCode(code == null ? null : code.trim())
                .orElseThrow(InviteNotFoundException::new);
        if (invite.isExpired(Instant.now())) {
            // 여기서 지우지 않는다 — 예외를 던지면 트랜잭션이 되돌아가 삭제도 함께 취소된다.
            // 만료된 코드는 다음 초대 발급 때 deleteExpired가 치운다
            throw new InviteExpiredException();
        }
        if (invite.groupId().equals(me.groupId())) {
            throw new AlreadyInGroupException();
        }
        userGroupRepository.findById(invite.groupId()).orElseThrow(GroupNotFoundException::new);

        final Long previousGroupId = me.groupId();
        userRepository.update(me.withGroupId(invite.groupId()));
        // 코드는 한 번 쓰면 버린다. 남겨 두면 24시간 동안 누구나 더 들어올 수 있다
        inviteRepository.delete(invite.code());
        deleteIfEmpty(previousGroupId);

        log.info("User moved group. userId={}, from={}, to={}", me.id(), previousGroupId, invite.groupId());
        return toResponse(userGroupRepository.findById(invite.groupId()).orElseThrow());
    }

    /**
     * 남은 사람이 없으면 그룹과 그 매물을 지운다 (규칙 4).
     *
     * <p>매물은 그룹에 딸리므로 그룹이 사라지면 볼 사람이 없습니다. 남겨 두면 <b>아무도 못
     * 보는 자료</b>가 계속 쌓입니다.
     */
    @Transactional
    public void deleteIfEmpty(Long groupId) {
        if (groupId == null) {
            return;
        }
        final long remaining = userRepository.findAll().stream()
                .filter(u -> u.role() != UserRole.ADMIN)
                .filter(u -> groupId.equals(u.groupId()))
                .count();
        if (remaining > 0) {
            return;
        }
        propertyRepository.deleteByGroupId(groupId);
        inviteRepository.deleteByGroupId(groupId);
        userGroupRepository.delete(groupId);
        log.info("Empty group removed with its properties. groupId={}", groupId);
    }

    /** 회원가입·admin 미지정 시 새 그룹을 만든다 (규칙 14). */
    @Transactional
    public UserGroup createForNewMember() {
        return userGroupRepository.save(
                new UserGroup(null, GroupNameGenerator.generate(), null, null, Instant.now()));
    }

    /**
     * admin이 그룹을 미리 만든다 (규칙 12 · 설계 I104).
     *
     * <p>이름을 비우면 무작위 한국어로 짓습니다. <b>이름은 겹칠 수 없습니다</b> — 같은 이름이
     * 둘이면 회원을 넣을 때 어느 쪽인지 화면에서 가릴 수 없습니다.
     *
     * <p><b>회원이 없는 그룹은 {@value #MAX_EMPTY_GROUPS}개까지입니다.</b> 빈 그룹은 아무
     * 쓸모가 없는데, 버튼을 누를 때마다 쌓이면 그룹 목록이 금방 못 쓰게 됩니다.
     */
    @Transactional
    public GroupResponse createByAdmin(String name) {
        if (countEmptyGroups() >= MAX_EMPTY_GROUPS) {
            throw new TooManyEmptyGroupsException();
        }
        final String resolved = resolveNewName(name);
        return toResponse(userGroupRepository.save(
                new UserGroup(null, resolved, null, null, Instant.now())));
    }

    /** 비우면 무작위로 짓되, 그것도 겹치면 다시 뽑는다. */
    private String resolveNewName(String name) {
        if (name != null && !name.isBlank()) {
            final String trimmed = name.trim();
            if (userGroupRepository.findByName(trimmed).isPresent()) {
                throw new DuplicateGroupNameException();
            }
            return trimmed;
        }
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            final String candidate = GroupNameGenerator.generate();
            if (userGroupRepository.findByName(candidate).isEmpty()) {
                return candidate;
            }
        }
        // 조합이 다 쓰였다면 뒤에 번호를 붙인다 — 만들지 못하는 것보다 낫다
        return GroupNameGenerator.generate() + " " + (countEmptyGroups() + 1);
    }

    private long countEmptyGroups() {
        return userGroupRepository.findAll().stream()
                .filter(g -> userRepository.findByGroupId(g.id()).isEmpty())
                .count();
    }

    /** admin 전용 — 회원은 다른 그룹이 있는지도 알 수 없다 (규칙 7). */
    public List<GroupResponse> listAll() {
        return userGroupRepository.findAll().stream().map(this::toResponse).toList();
    }

    private GroupResponse toResponse(UserGroup group) {
        final long members = userRepository.findAll().stream()
                .filter(u -> group.id().equals(u.groupId()))
                .count();
        return new GroupResponse(group.id(), group.name(), (int) members,
                group.slackWebhookUrl(), group.createdAt());
    }
}
