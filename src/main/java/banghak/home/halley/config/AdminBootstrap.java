package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_LOGIN = "admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 정해 준 최초 비밀번호. 비어 있으면 만들어 쓴다 (설계 I296) */
    private final String configuredPassword;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${halley.admin.initial-password:}") String configuredPassword) {
        this.configuredPassword = configuredPassword == null ? "" : configuredPassword.trim();
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByLoginId(ADMIN_LOGIN).isPresent()) {
            return;
        }
        final boolean configured = !configuredPassword.isBlank();
        final String password = configured ? configuredPassword : randomPassword();
        userRepository.save(new User(
                null, ADMIN_LOGIN, "admin", null, passwordEncoder.encode(password), UserRole.ADMIN,
                null, null, null,
                true, false, 0L, 0L, 0L, true,
                null, null, null
        ));
        log.info("==========================================================");
        log.info("  Admin account initialized. username : {}", ADMIN_LOGIN);
        if (configured) {
            // 정해 준 비밀번호는 로그에 남기지 않는다 (설계 I296) — 중앙 수집·백업에 자격증명이 남는다
            log.info("  password : (HALLEY_ADMIN_INITIAL_PASSWORD 에서 받음)");
        } else {
            log.warn("  password : {}", password);
            log.warn("  최초 비밀번호를 로그에서 읽어야 한다. 운영이라면 HALLEY_ADMIN_INITIAL_PASSWORD 를 두라");
        }
        log.info("  Please change the password after first login.");
        log.info("==========================================================");
    }

    private String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
