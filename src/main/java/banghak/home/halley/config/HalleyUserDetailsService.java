package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class HalleyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public HalleyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        return userRepository.findByLoginId(loginId)
                .map(HalleyUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + loginId));
    }
}
