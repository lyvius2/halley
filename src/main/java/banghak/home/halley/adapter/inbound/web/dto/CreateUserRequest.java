package banghak.home.halley.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import banghak.home.halley.domain.user.UserRole;

import java.math.BigDecimal;

public record CreateUserRequest(
        @NotBlank @Size(max = 50) String loginId,
        @NotBlank @Size(max = 50) String nickname,
        /** 넣을 그룹. 비우면 새 그룹을 만들어 배정한다  */
        Long groupId,
        @NotBlank @Size(min = 8, max = 100) String password,
        UserRole role,
        String workplaceName,
        BigDecimal workplaceLat,
        BigDecimal workplaceLng,
        Long availableBudget,
        Long annualIncome,
        Long existingLoan
) {
}
