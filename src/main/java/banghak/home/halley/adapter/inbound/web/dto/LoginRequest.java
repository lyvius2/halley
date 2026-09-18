package banghak.home.halley.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(max = 100) String password,
        Boolean rememberMe) {

    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
