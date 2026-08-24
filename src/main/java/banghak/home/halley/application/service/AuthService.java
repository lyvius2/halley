package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.ApiException;
import banghak.home.halley.adapter.inbound.web.dto.AuthResponse;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.config.HalleyUserDetails;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;

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

    public AuthResponse login(String email, String password) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            return toAuthResponse((HalleyUserDetails) auth.getPrincipal());
        } catch (DisabledException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "비활성화된 계정입니다");
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "이메일 또는 비밀번호가 올바르지 않습니다");
        }
    }

    public AuthResponse session() {
        return toAuthResponse(current());
    }

    public void changePassword(String currentPassword, String newPassword) {
        HalleyUserDetails principal = current();
        if (!passwordEncoder.matches(currentPassword, principal.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "현재 비밀번호가 올바르지 않습니다");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다"));

        userRepository.update(new User(
                user.id(), user.nickname(), user.email(), passwordEncoder.encode(newPassword), user.role(),
                user.workplaceName(), user.workplaceLat(), user.workplaceLng(),
                false, user.availableBudget(), user.enabled(),
                user.disabledAt(), user.disabledBy(), user.createdAt()));

        principal.setMustChangePassword(false);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    private HalleyUserDetails current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof HalleyUserDetails principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다");
        }
        return principal;
    }

    private AuthResponse toAuthResponse(HalleyUserDetails principal) {
        return new AuthResponse(
                principal.getId(),
                principal.getNickname(),
                UserRole.valueOf(principal.getRole()),
                principal.isMustChangePassword());
    }
}
