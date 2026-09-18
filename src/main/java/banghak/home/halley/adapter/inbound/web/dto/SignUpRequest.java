package banghak.home.halley.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 스스로 하는 회원가입 (설계 I89 · 규칙 13·14).
 *
 * <p>그룹은 받지 않습니다 — <b>가입과 동시에 새 그룹이 자동으로 생깁니다.</b> 가입하는
 * 순간에는 그룹이 무엇인지도 모르는 상태라 이름을 물어도 의미 없는 값이 들어갑니다.
 */
public record SignUpRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "영문·숫자·._- 만 쓸 수 있습니다") String loginId,
        @NotBlank @Size(max = 50) String nickname,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
