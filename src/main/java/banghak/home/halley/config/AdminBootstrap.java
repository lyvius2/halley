package banghak.home.halley.config;

import banghak.home.halley.application.port.out.persistence.UserRepository;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin@halley.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return;
        }
        String password = randomPassword();
        userRepository.save(new User(
                null, "admin", ADMIN_EMAIL, passwordEncoder.encode(password), UserRole.ADMIN,
                null, null, null,
                true, 0L, true,
                null, null, null
        ));
        log.info("★ 초기 관리자 계정 생성 ★ email={} / password={}", ADMIN_EMAIL, password);
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
