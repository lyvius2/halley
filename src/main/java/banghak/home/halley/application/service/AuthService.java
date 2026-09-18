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
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class AuthService {

    /** 로그인 상태 유지 기간. 30일 — 그 뒤에는 다시 물어본다  */
    private static final Duration REMEMBER_DURATION = Duration.ofDays(30);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /** 무차별 대입 방어  */
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public AuthResponse login(String loginId, String password, boolean rememberMe,
                              HttpServletRequest request, HttpServletResponse response) {
        // 비밀번호를 보기 전에 센다 — 잠겼으면 맞는 비밀번호도 막는다.
        // 프록시 뒤에서는 forward-headers-strategy 가 있어야 이 주소가 실제 손님이다
        final String address = request.getRemoteAddr();
        loginAttemptLimiter.check(loginId, address);
        try {
            final Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginId, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            loginAttemptLimiter.reset(loginId, address);
            if (rememberMe) {
                rememberSession(request, response);
            }
            return toAuthResponse((HalleyUserDetails) Objects.requireNonNull(auth.getPrincipal()), request);
        } catch (DisabledException e) {
            // 비활성 계정은 비밀번호가 틀린 것이 아니다 — 세지 않는다
            throw new AccountDisabledException();
        } catch (AuthenticationException e) {
            loginAttemptLimiter.recordFailure(loginId, address);
            throw new InvalidCredentialsException();
        }
    }

    private void rememberSession(HttpServletRequest request, HttpServletResponse response) {
        final HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((int) REMEMBER_DURATION.toSeconds());
        // 스킴을 그대로 따른다 — 로컬(http)에서 secure 를 켜면 쿠키가 아예 안 실린다
        final ResponseCookie cookie = ResponseCookie.from("JSESSIONID", session.getId())
                .path("/")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .maxAge(REMEMBER_DURATION)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
        final jakarta.servlet.http.HttpSession session = request.getSession(true);
        if (session == null) {
            return null;
        }
        final long remaining = (session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L)
                - System.currentTimeMillis();
        return (int) Math.max(0, remaining / 1000);
    }
}
