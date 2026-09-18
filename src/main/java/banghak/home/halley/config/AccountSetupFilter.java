package banghak.home.halley.config;

import banghak.home.halley.domain.user.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AccountSetupFilter extends OncePerRequestFilter {

    private static final Set<String> PASSWORD_STEP_ALLOWED = Set.of(
            "/api/auth/password", "/api/auth/logout");

    /** 프로필 단계에서는 세션 확인·프로필 저장·주소 검색까지 허용해야 설정을 마칠 수 있다.  */
    private static final Set<String> PROFILE_STEP_ALLOWED = Set.of(
            "/api/auth/password", "/api/auth/logout", "/api/auth/session",
            "/api/users/me", "/api/users/me/profile", "/api/geo/search");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof HalleyUserDetails principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 관리자는 이 흐름을 타지 않는다. 직장·보유 현금은 매물을 보는 사람의
        // 값이고 admin은 어느 그룹에도 속하지 않는다 — 요구하면 관리 자체가 막힌다
        if (UserRole.ADMIN.name().equals(principal.getRole())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (principal.isMustChangePassword() && !PASSWORD_STEP_ALLOWED.contains(uri)) {
            reject(response, "MUST_CHANGE_PASSWORD");
            return;
        }
        // 화면과 같은 기준으로 막는다. 한쪽은 확인 여부로, 다른 쪽은
        // 완성 여부로 보면 확인을 마쳐도 API가 계속 403을 준다
        if (!principal.isProfileConfirmed() && !PROFILE_STEP_ALLOWED.contains(uri)) {
            reject(response, "PROFILE_SETUP_REQUIRED");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
