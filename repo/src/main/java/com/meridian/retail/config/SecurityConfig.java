package com.meridian.retail.config;

import com.meridian.retail.security.CustomAuthenticationFailureHandler;
import com.meridian.retail.security.CustomAuthenticationSuccessHandler;
import com.meridian.retail.security.NonceValidationFilter;
import com.meridian.retail.security.RateLimitFilter;
import com.meridian.retail.security.RequestSigningFilter;
import com.meridian.retail.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Spring Security configuration.
 *
 * Form-login (local username/password only — SPEC.md). All POST forms are CSRF-protected
 * via Spring Security's standard mechanism. Three custom filters are inserted BEFORE
 * UsernamePasswordAuthenticationFilter:
 *
 *   1. RateLimitFilter        — 60/min standard, 10/min export, per authenticated user
 *   2. NonceValidationFilter  — anti-replay for /admin/** and /approval/dual-approve/**
 *   3. RequestSigningFilter   — HMAC-SHA256 signature check for /admin/**
 *
 * Permit-all is intentionally narrow: only the login page, the locally-rendered CAPTCHA
 * image endpoint, the /actuator/health probe, and bundled vendor static assets.
 *
 * BCryptPasswordEncoder strength=12 matches the seed-data hashes in V12__seed_data.sql.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final RateLimitFilter rateLimitFilter;
    private final NonceValidationFilter nonceValidationFilter;
    private final RequestSigningFilter requestSigningFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength = 12 matches the seed-data hashes; do not lower without re-seeding.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder b = http.getSharedObject(AuthenticationManagerBuilder.class);
        b.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return b.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // CSRF cookie is readable by JS so HTMX can include the token header.
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .authorizeHttpRequests(auth -> auth
                // Public surface — kept intentionally minimal.
                .requestMatchers("/login", "/captcha/**", "/health", "/actuator/health",
                                 "/css/**", "/js/**", "/vendor/**", "/error/**").permitAll()
                // Role-restricted areas.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/approval/**").hasAnyRole("ADMIN", "REVIEWER")
                .requestMatchers("/analytics/export/**").hasAnyRole("ADMIN", "FINANCE")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(3)   // allow a few concurrent sessions per user
            )
            .exceptionHandling(eh -> eh
                .accessDeniedPage("/error/403")
            );

        // Custom filters in order. Rate limit runs first so an attacker hammering /login
        // gets throttled before any heavy work; nonce validation runs before signing so
        // we don't waste time computing HMAC for replays.
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(nonceValidationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(requestSigningFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
