package com.curo.curo_backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Super-admin-only facility verification — must come BEFORE the broader permitAll below
                        .requestMatchers("/hospital/*/verify", "/hospital/*/reject",
                                "/pharmacy-facility/*/verify", "/pharmacy-facility/*/reject")
                        .hasRole("SUPER_ADMIN")
                        // New patient-data endpoints decide access in AccessGuard, so /patient/** is no longer public
                        .requestMatchers("/patient/me", "/patient/me/**").hasRole("PATIENT")
                        .requestMatchers("/doses/**").hasRole("PATIENT")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/patient/*/medications").hasAnyRole("DOCTOR", "PHARMACY", "CAREGIVER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/prescription/*/history").hasAnyRole("DOCTOR", "PHARMACY")
                        .requestMatchers("/auth/**", "/hospital/**", "/pharmacy-facility/**").permitAll()
                        .requestMatchers("/drugs/**").hasAnyRole("DOCTOR", "ADMIN", "PHARMACY")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/reception/visit/**").hasAnyRole("DOCTOR", "RECEPTION", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/reception/queue/**").hasAnyRole("DOCTOR", "RECEPTION", "ADMIN")
                        .requestMatchers("/reception/**").hasAnyRole("RECEPTION", "ADMIN")
                        .requestMatchers("/prescription/by-qr/**").hasAnyRole("PHARMACY", "ADMIN")
                        .requestMatchers("/prescription/recent", "/prescription/for-pharmacy/**").hasAnyRole("DOCTOR", "ADMIN", "PHARMACY")
                        .requestMatchers("/prescription/group/**").hasAnyRole("DOCTOR", "ADMIN", "PHARMACY")
                        .requestMatchers("/prescription/for-patient/**").hasAnyRole("DOCTOR", "ADMIN", "PHARMACY")
                        .requestMatchers("/prescription/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/pharmacy/**").hasAnyRole("PHARMACY", "ADMIN")
                        .requestMatchers("/ai/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/staff/**").hasAnyRole("RECEPTION", "DOCTOR", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/doctor/leave/**").hasRole("DOCTOR")
                        .requestMatchers("/doctor/profile").hasRole("DOCTOR")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}