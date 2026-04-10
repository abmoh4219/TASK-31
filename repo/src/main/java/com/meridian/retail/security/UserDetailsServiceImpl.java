package com.meridian.retail.security;

import com.meridian.retail.entity.User;
import com.meridian.retail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads users from the local users table for Spring Security.
 *
 * - Returns a Spring Security {@code User} with role mapped to "ROLE_<role>" so that
 *   `hasRole('ADMIN')` and `hasAuthority('ROLE_ADMIN')` both work as expected.
 * - If the row exists but {@code is_active=false}, the returned UserDetails is marked
 *   disabled — Spring Security will then short-circuit authentication with a
 *   DisabledException.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .disabled(!u.isActive())
                .accountLocked(false)   // lockout is enforced separately by AccountLockoutService
                .accountExpired(false)
                .credentialsExpired(false)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))
                .build();
    }
}
