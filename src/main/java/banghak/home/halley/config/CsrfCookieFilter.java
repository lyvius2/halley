package banghak.home.halley.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CSRF 토큰을 요청마다 한 번 읽어 쿠키에 적히게 한다 (설계 I295).
 *
 * <p>{@code CookieCsrfTokenRepository} 는 <b>누가 토큰을 읽을 때</b> 쿠키를 씁니다. 화면은
 * 셸(`GET /`)만 받고 API 를 부르므로, 여기서 건드려 주지 않으면 첫 POST 가 토큰 없이 나가
 * 403 을 맞습니다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
