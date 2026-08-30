package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 매물 접근을 그룹 경계로 막는 <b>단 하나의 길목</b> (설계 I87).
 *
 * <p>매물을 읽는 자리가 스무 곳이 넘습니다. 각자 그룹을 확인하게 두면 <b>한 곳만 빠져도 남의
 * 그룹 자료가 샙니다</b> — 그리고 빠졌다는 사실은 아무 데도 드러나지 않습니다. 그래서 사용자
 * 요청에서 출발하는 모든 경로가 여기를 지나게 합니다.
 *
 * <p><b>없는 것처럼 답합니다.</b> 남의 그룹 매물에 접근하면 403이 아니라 404입니다.
 * 403은 "그 번호의 매물이 존재하기는 한다"는 사실을 알려 줍니다.
 *
 * <p>배경 작업(보정·AI·알림)은 이미 인가된 매물 번호로 도는 것이라 여기를 거치지 않습니다 —
 * 거치게 하면 로그인 사용자가 없어 전부 막힙니다.
 */
@Service
public class PropertyAccessGuard {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public PropertyAccessGuard(PropertyRepository propertyRepository, UserRepository userRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    /** 볼 수 있는 매물이면 돌려주고, 아니면 없는 것으로 친다. */
    public Property require(Long propertyId) {
        final Property property = propertyRepository.findById(propertyId)
                .orElseThrow(NotFoundListingsException::new);
        if (!canSee(property)) {
            throw new NotFoundListingsException();
        }
        return property;
    }

    public boolean canSee(Property property) {
        if (isAdmin()) {
            return true;
        }
        final Long myGroup = currentGroupId().orElse(null);
        return myGroup != null && myGroup.equals(property.groupId());
    }

    /** admin은 어느 그룹에도 속하지 않고 전부 본다 (설계 규칙 5). */
    public boolean isAdmin() {
        return currentUser().map(u -> u.role() == UserRole.ADMIN).orElse(false);
    }

    public Optional<Long> currentGroupId() {
        return currentUser().map(User::groupId);
    }

    public Optional<User> currentUser() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HalleyUserDetails principal) {
            return userRepository.findById(principal.getId());
        }
        return Optional.empty();
    }
}
