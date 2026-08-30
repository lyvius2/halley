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
        User saved = userRepository.save(user("혜미", "hyemi"));

        assertThat(saved.id()).isNotNull();

        Optional<User> found = userRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().loginId()).isEqualTo("hyemi");
        assertThat(found.get().nickname()).isEqualTo("혜미");
        assertThat(found.get().role()).isEqualTo(UserRole.MEMBER);
        assertThat(found.get().createdAt()).isNotNull();
    }

    @Test
    void findByLoginIdAndNickname() {
        userRepository.save(user("윤선", "yoon"));

        assertThat(userRepository.findByLoginId("yoon")).isPresent();
        assertThat(userRepository.findByNickname("윤선")).isPresent();
        assertThat(userRepository.findByLoginId("nobody")).isEmpty();
    }

    @Test
    void findAll() {
        userRepository.save(user("a", "a"));
        userRepository.save(user("b", "b"));

        List<User> all = userRepository.findAll();
        assertThat(all).extracting(User::nickname).contains("a", "b");
    }

    @Test
    void delete() {
        User saved = userRepository.save(user("c", "c"));
        userRepository.delete(saved.id());

        assertThat(userRepository.findById(saved.id())).isEmpty();
    }

    private User user(String nickname, String loginId) {
        return new User(
                null, loginId, nickname,null, "hash", UserRole.MEMBER,
                null, null, null,
                true, 0L, null, null, true,
                null, null, null
        );
    }
}
