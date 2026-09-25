package banghak.home.halley.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "영문·숫자·._- 만 쓸 수 있습니다") String loginId,
        @NotBlank @Size(max = 50) String nickname,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
