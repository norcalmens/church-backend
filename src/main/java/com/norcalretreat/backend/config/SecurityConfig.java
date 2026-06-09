package com.norcalretreat.backend.config;

import com.norcalretreat.backend.security.JwtAuthenticationEntryPoint;
import com.norcalretreat.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints (register is admin-only)
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/api/auth/**").permitAll()

                        // Health check
                        .requestMatchers("/api/health").permitAll()

                        // Stripe webhook (public callback)
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()

                        // Stripe config (needed by frontend before login)
                        .requestMatchers(HttpMethod.GET, "/api/payments/config").permitAll()

                        // Public donation endpoint (anonymous giving)
                        .requestMatchers(HttpMethod.POST, "/api/payments/create-payment-intent").permitAll()

                        // Donations: public create + confirm, admin-only list
                        .requestMatchers(HttpMethod.POST, "/api/donations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/donations/*/confirm").permitAll()
                        .requestMatchers("/api/donations/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Payment plans: tokenized public portal endpoints, admin-only everything else
                        .requestMatchers(HttpMethod.GET,  "/api/payment-plans/by-token/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payment-plans/by-token/*/pay").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payment-plans/by-token/*/payments/*/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payment-plans/by-token/*/recurring/checkout").permitAll()
                        .requestMatchers("/api/payment-plans/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Menu config (GET is public, PUT is admin-only)
                        .requestMatchers(HttpMethod.GET, "/api/menu-config/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/menu-config/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Zoom links (public GET active list, admin-only mutations)
                        .requestMatchers(HttpMethod.GET, "/api/zoom-links").permitAll()
                        .requestMatchers("/api/zoom-links/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Admin-only endpoints
                        .requestMatchers("/api/registrations/all").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/api/registrations/stats").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/registrations/admin/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PATCH,  "/api/registrations/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Public registration endpoints (anonymous registration flow)
                        .requestMatchers(HttpMethod.GET,  "/api/registrations/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registrations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registrations/*/payment-intent").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registrations/*/confirm-payment").permitAll()

                        // Remaining registration and payment endpoints require authentication
                        .requestMatchers("/api/registrations/**").authenticated()
                        .requestMatchers("/api/payments/**").authenticated()

                        // Everything else is public
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://norcalmensretreat.com",
                "https://www.norcalmensretreat.com",
                "https://norcalmensretreat.org",
                "https://www.norcalmensretreat.org",
                "https://norcalmensretreat.net",
                "https://www.norcalmensretreat.net",
                "https://*.up.railway.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
