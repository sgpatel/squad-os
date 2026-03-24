package io.sentinel.backend.security;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class SentinelUserDetailsService implements UserDetailsService {
    private final UserRepository repo;
    public SentinelUserDetailsService(UserRepository repo) { this.repo = repo; }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repo.findByUsername(username)
            .map(u -> new org.springframework.security.core.userdetails.User(
                u.username, u.passwordHash,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name()))))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}