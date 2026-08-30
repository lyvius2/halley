package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.AuthResponse;
import banghak.home.halley.config.exception.AccountDisabledException;
import banghak.home.halley.config.exception.AuthenticationRequiredException;
import banghak.home.halley.config.exception.InvalidCredentialsException;
import banghak.home.halley.config.exception.InvalidPasswordException;
import banghak.home.halley.config.exception.NotFoundUserException;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(String loginId, String password, HttpServletRequest request) {
        try {
            final Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginId, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            return toAuthResponse((HalleyUserDetails) Objects.requireNonNull(auth.getPrincipal()), request);
        } catch (DisabledException e) {
            throw new AccountDisabledException();
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
    }

    public AuthResponse session(HttpServletRequest request) {
        return toAuthResponse(current(), request);
    }

    public void changePassword(String currentPassword, String newPassword) {
        final HalleyUserDetails principal = current();
        if (!passwordEncoder.matches(currentPassword, principal.getPassword())) {
            throw new InvalidPasswordException();
        }
        final User user = userRepository.findById(principal.getId())
                .orElseThrow(NotFoundUserException::new);

        userRepository.update(new User(
                user.id(), user.loginId(), user.nickname(), user.groupId(),
                passwordEncoder.encode(newPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                false, false, user.availableBudget(),
                user.annualIncomeOrZero(), user.existingLoanOrZero(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));

        principal.setMustChangePassword(false);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    private HalleyUserDetails current() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof HalleyUserDetails principal)) {
            throw new AuthenticationRequiredException();
        }
        return principal;
    }

    private AuthResponse toAuthResponse(HalleyUserDetails principal, HttpServletRequest request) {
        return new AuthResponse(
                principal.getId(),
                principal.getNickname(),
                UserRole.valueOf(principal.getRole()),
                principal.isMustChangePassword(),
                principal.isProfileComplete(),
                principal.isProfileConfirmed(),
                remainingSessionSeconds(request));
    }

    private Integer remainingSessionSeconds(HttpServletRequest request) {
        final jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        final long remaining = (session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L)
                - System.currentTimeMillis();
        return (int) Math.max(0, remaining / 1000);
    }
}
