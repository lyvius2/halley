package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.UserRepository;
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

    private static final String ADMIN_LOGIN = "admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByLoginId(ADMIN_LOGIN).isPresent()) {
            return;
        }
        String password = randomPassword();
        userRepository.save(new User(
                null, ADMIN_LOGIN, "admin", null, passwordEncoder.encode(password), UserRole.ADMIN,
                null, null, null,
                true, 0L, true,
                null, null, null
        ));
        log.info("==========================================================");
        log.info("  Admin account initialized.");
        log.info("  username : {}", ADMIN_LOGIN);
        log.info("  password : {}", password);
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
