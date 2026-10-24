package com.curo.curo_backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.Arrays;
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

    // Comma-separated exact origins, from CORS_ALLOWED_ORIGINS (see application.properties). Credentials are allowed,
    // so a wildcard origin is refused at startup rather than quietly accepted.
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8081}")
    private String allowedOrigins;

    static List<String> parseOrigins(String csv) {
        List<String> origins = Arrays.stream(csv == null ? new String[0] : csv.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toList();
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list exact origins; wildcards are not allowed with credentials");
            }
        }
        return origins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

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
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/patient/*/summary").hasRole("DOCTOR")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/patient/*/medications").hasAnyRole("DOCTOR", "PHARMACY", "CAREGIVER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/patient/*/dose-schedule").hasAnyRole("DOCTOR", "CAREGIVER")
                        .requestMatchers("/caregiver-links", "/caregiver-links/**").hasAnyRole("PATIENT", "CAREGIVER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/prescription/*/history").hasAnyRole("DOCTOR", "PHARMACY")
                        // only signup and login are public; everything else under /auth (such as /auth/me) needs a valid token
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/signup", "/auth/login").permitAll()
                        .requestMatchers("/hospital/**", "/pharmacy-facility/**").permitAll()
                        .requestMatchers("/drugs/**").hasAnyRole("DOCTOR", "ADMIN", "PHARMACY")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/reception/check-in").hasRole("RECEPTION")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/reception/doctors").hasRole("RECEPTION")
                        .requestMatchers("/doctor/queue", "/doctor/queue/**").hasRole("DOCTOR")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/doctor/patients/*/prescriptions/active").hasRole("DOCTOR")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/prescriptions/*/extend").hasRole("DOCTOR")
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
                        .requestMatchers("/admin/users", "/admin/users/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/doctor/leave/**").hasRole("DOCTOR")
                        .requestMatchers("/doctor/profile").hasRole("DOCTOR")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}