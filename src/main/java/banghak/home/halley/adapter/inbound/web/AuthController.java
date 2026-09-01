package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.AuthResponse;
import banghak.home.halley.adapter.inbound.web.dto.LoginRequest;
import banghak.home.halley.adapter.inbound.web.dto.PasswordChangeRequest;
import banghak.home.halley.application.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import banghak.home.halley.adapter.inbound.web.dto.PublicConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private final boolean signUpOpen;

    public AuthController(AuthService authService,
                          @Value("${membership.sign-up.open:true}") boolean signUpOpen) {
        this.authService = authService;
        this.signUpOpen = signUpOpen;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.login(request.loginId(), request.password(), request.remember(),
                httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordChangeRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
    }

    /**
     * 로그인 전에 화면이 알아야 하는 설정 (설계 I95).
     *
     * <p>세션 조회는 로그아웃 상태에서 401이라 여기에 담을 수 없습니다.
     */
    @GetMapping("/config")
    public PublicConfigResponse config() {
        return new PublicConfigResponse(signUpOpen);
    }

    @GetMapping("/session")
    public AuthResponse session(HttpServletRequest request) {
        return authService.session(request);
    }
}
