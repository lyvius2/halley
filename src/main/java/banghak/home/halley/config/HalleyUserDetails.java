package banghak.home.halley.config;

import banghak.home.halley.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class HalleyUserDetails implements UserDetails {

    private final Long id;
    private final String nickname;
    private final String email;
    private final String password;
    private final String role;
    private final boolean enabled;
    private boolean mustChangePassword;

    public HalleyUserDetails(User user) {
        this.id = user.id();
        this.nickname = user.nickname();
        this.email = user.email();
        this.password = user.passwordHash();
        this.role = user.role().name();
        this.enabled = user.enabled();
        this.mustChangePassword = user.mustChangePassword();
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
