package banghak.home.halley.adapter.inbound.web;

import banghak.home.halley.adapter.inbound.web.dto.CreateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.ResetPasswordResponse;
import banghak.home.halley.adapter.inbound.web.dto.UpdateStatusRequest;
import banghak.home.halley.adapter.inbound.web.dto.UpdateUserRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserResponse;
import banghak.home.halley.adapter.inbound.web.dto.ProfileRequest;
import banghak.home.halley.application.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import banghak.home.halley.adapter.inbound.web.dto.NicknameCheckResponse;
import banghak.home.halley.adapter.inbound.web.dto.SignUpRequest;
import banghak.home.halley.adapter.inbound.web.dto.WithdrawRequest;
import org.springframework.web.bind.annotation.RequestParam;
import banghak.home.halley.adapter.inbound.web.dto.UserDebtRequest;
import banghak.home.halley.adapter.inbound.web.dto.UserDebtResponse;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 스스로 하는 회원가입 (규칙 13·14). 로그인 없이 부를 수 있어야 한다. */
    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signUp(@RequestBody SignUpRequest request) {
        return userService.signUp(request);
    }

    /** 닉네임 중복 확인 (규칙 17). */
    @GetMapping("/nickname-check")
    public NicknameCheckResponse checkNickname(@RequestParam("nickname") String nickname) {
        return userService.checkNickname(nickname);
    }

    /** 종류별 기존 부채 (설계 I92). */
    @GetMapping("/me/debts")
    public List<UserDebtResponse> myDebts() {
        return userService.myDebts();
    }

    @PutMapping("/me/debts")
    public List<UserDebtResponse> replaceMyDebts(@RequestBody List<UserDebtRequest> requests) {
        return userService.replaceMyDebts(requests);
    }

    /** 회원 탈퇴 (규칙 15·16). 비밀번호를 다시 받는다. */
    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@RequestBody WithdrawRequest request) {
        userService.withdraw(request);
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.me();
    }

    @PutMapping("/me/profile")
    public UserResponse updateProfile(@RequestBody ProfileRequest request) {
        return userService.updateProfile(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(id, request.enabled());
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id);
    }
}
