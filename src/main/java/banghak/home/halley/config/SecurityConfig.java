package banghak.home.halley.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AccountSetupFilter accountSetupFilter,
                                                   JsonAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(securityContext -> securityContext.requireExplicitSave(false))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        // 회원가입은 로그인 전에 부른다 (설계 I89 · 규칙 13).
                        // 닉네임 확인도 가입 화면에서 쓰므로 함께 연다
                        .requestMatchers(HttpMethod.GET, "/api/auth/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/sign-up").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/nickname-check").permitAll()
                        .requestMatchers("/api/users/me/**").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/criteria/weights").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 올린 사진도 자료다 (설계 I205). 정적 리소스로 열어 두는 바람에
                        // 아래 permitAll 에 걸려 <b>로그인 없이 URL 만 알면</b> 열렸다.
                        // 어느 그룹의 사진인지는 UploadedImageController 가 한 번 더 본다
                        .requestMatchers("/uploads/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterAfter(accountSetupFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
