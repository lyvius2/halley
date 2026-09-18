package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 최초 admin 비밀번호 (설계 I296).
 *
 * <p>만들어서 INFO 로그에 적고 있었다. 중앙 로그 수집·백업에 자격증명이 남는다.
 * 운영은 정해 준 값을 쓰고 로그에는 남기지 않는다.
 */
@DisplayName("최초 admin 비밀번호 (설계 I296)")
class AdminBootstrapTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserRepository users = mock(UserRepository.class);

    @Test
    @DisplayName("정해 준 비밀번호가 있으면 그것으로 만든다")
    void usesTheConfiguredPassword() {
        // given
        when(users.findByLoginId(anyString())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        new AdminBootstrap(users, encoder, "Chosen!Pass9").run(new DefaultApplicationArguments());

        // then
        final ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(encoder.matches("Chosen!Pass9", saved.getValue().passwordHash())).isTrue();
    }

    @Test
    @DisplayName("비어 있으면 만들어 쓴다 — 로컬 편의")
    void generatesWhenNothingIsConfigured() {
        // given
        when(users.findByLoginId(anyString())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        new AdminBootstrap(users, encoder, "  ").run(new DefaultApplicationArguments());

        // then — 무엇이든 만들어졌고, 빈 값은 아니다
        final ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(encoder.matches("", saved.getValue().passwordHash())).isFalse();
    }

    @Test
    @DisplayName("이미 있으면 손대지 않는다")
    void leavesAnExistingAdminAlone() {
        // given
        when(users.findByLoginId(anyString())).thenReturn(Optional.of(mock(User.class)));

        // when
        new AdminBootstrap(users, encoder, "x").run(new DefaultApplicationArguments());

        // then
        verify(users, never()).save(any());
    }
}
