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

    /** 로그인 상태 유지 기간 (설계 I190). 30일 — 그 뒤에는 다시 물어본다 */
    private static final Duration REMEMBER_DURATION = Duration.ofDays(30);

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

    /**
     * @param rememberMe 켜면 <b>로그아웃할 때까지</b> 유지한다 (설계 I190)
     */
    public AuthResponse login(String loginId, String password, boolean rememberMe,
                              HttpServletRequest request, HttpServletResponse response) {
        try {
            final Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginId, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (rememberMe) {
                rememberSession(request, response);
            }
            return toAuthResponse((HalleyUserDetails) Objects.requireNonNull(auth.getPrincipal()), request);
        } catch (DisabledException e) {
            throw new AccountDisabledException();
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
    }

    /**
     * 로그인 상태를 오래 끈다 (설계 I190).
     *
     * <p>두 가지를 같이 해야 합니다 — <b>하나만 하면 안 됩니다.</b>
     *
     * <ol>
     *   <li>서버 쪽 수명(`maxInactiveInterval`)을 늘린다 — 안 늘리면 30분 뒤 세션이 사라진다</li>
     *   <li>쿠키에 만료를 준다 — 안 주면 <b>브라우저를 닫는 순간</b> 쿠키가 날아간다</li>
     * </ol>
     *
     * <p><b>세션은 메모리에 있습니다.</b> 서버를 다시 띄우면 유지 여부와 상관없이
     * 전부 로그아웃됩니다 — 배포할 때마다 그렇습니다.
     */
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

    /**
     * 세션이 얼마나 남았는지 (설계 I120).
     *
     * <p><b>로그인 응답에서는 세션이 아직 없습니다.</b> Spring Security가 인증을 저장하기 전이라
     * {@code getSession(false)}가 null을 돌려주고, 그러면 화면이 남은 시간을 모르는 채로
     * 시작합니다 — 세션 경고가 영영 뜨지 않았습니다.
     *
     * <p>그래서 <b>없으면 만들어</b> 답합니다. 어차피 로그인 직후 첫 요청에서 만들어질
     * 세션이고, 여기서 만드나 거기서 만드나 같습니다.
     */
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
