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

/** 매물 접근을 그룹 경계로 막는 단 하나의 길목. */
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

 /** admin은 어느 그룹에도 속하지 않고 전부 본다. */
    public boolean isAdmin() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof HalleyUserDetails principal
                && UserRole.ADMIN.name().equals(principal.getRole());
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
