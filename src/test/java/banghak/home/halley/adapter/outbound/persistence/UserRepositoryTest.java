package banghak.home.halley.adapter.outbound.persistence;

import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.User;
import banghak.home.halley.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindById() {
        User saved = userRepository.save(user("혜미", "hyemi@example.com"));

        assertThat(saved.id()).isNotNull();

        Optional<User> found = userRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("hyemi@example.com");
        assertThat(found.get().nickname()).isEqualTo("혜미");
        assertThat(found.get().role()).isEqualTo(UserRole.MEMBER);
        assertThat(found.get().createdAt()).isNotNull();
    }

    @Test
    void findByEmailAndNickname() {
        userRepository.save(user("윤선", "yoon@example.com"));

        assertThat(userRepository.findByEmail("yoon@example.com")).isPresent();
        assertThat(userRepository.findByNickname("윤선")).isPresent();
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void findAll() {
        userRepository.save(user("a", "a@example.com"));
        userRepository.save(user("b", "b@example.com"));

        List<User> all = userRepository.findAll();
        assertThat(all).extracting(User::email).contains("a@example.com", "b@example.com");
    }

    @Test
    void delete() {
        User saved = userRepository.save(user("c", "c@example.com"));
        userRepository.delete(saved.id());

        assertThat(userRepository.findById(saved.id())).isEmpty();
    }

    private User user(String nickname, String email) {
        return new User(
                null, email.split("@")[0], nickname, email, "hash", UserRole.MEMBER,
                null, null, null,
                true, 0L, null, null, true,
                null, null, null
        );
    }
}
