package com.example.meetings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/css/**", "/js/**").permitAll()
                        // Public so calendar apps can subscribe without sending credentials. The
                        // unguessable token in the URL is the only access control here.
                        .requestMatchers("/ical/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/calendar", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // The H2 console serves frames; carve a CSRF/frame exception for it.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new AntPathRequestMatcher("/h2-console/**"),
                        new AntPathRequestMatcher("/ical/**")))
                .headers(headers -> headers.frameOptions(f -> f.sameOrigin()));

        return http.build();
    }
}
