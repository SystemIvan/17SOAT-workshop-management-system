package br.com.fiap.workshop_management_system.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central authorization matrix (technical-spec.md — jwt-authentication). A new endpoint must get an
 * explicit rule here before it is reachable without review; requestMatchers are evaluated in the order
 * declared, most specific first.
 */
@Configuration
@ConditionalOnBean(JwtAuthenticationFilter.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/users").hasAuthority("ADMIN")

                        .requestMatchers("/api/customers/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/vehicles/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/technicians/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/stock-items/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/stock-reservations/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/purchase-demands/**").hasAnyAuthority("MANAGER", "ADMIN")
                        .requestMatchers("/api/purchase-orders/**").hasAnyAuthority("MANAGER", "ADMIN")

                        // Customer tracking: any authenticated role, no resource-ownership check (out of
                        // scope, see functional-spec.md). Declared before the general service-orders rule.
                        .requestMatchers(HttpMethod.GET, "/api/service-orders/*/status").authenticated()

                        // Estimate generation lives under /service-orders/{id}/estimates; declared before
                        // the general service-orders rule so it does not inherit the TECHNICIAN grant below.
                        .requestMatchers(HttpMethod.POST, "/api/service-orders/*/estimates")
                        .hasAnyAuthority("MANAGER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/estimates/*/decisions")
                        .hasAnyAuthority("CUSTOMER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/estimates/**")
                        .hasAnyAuthority("CUSTOMER", "MANAGER", "ADMIN")

                        .requestMatchers("/api/service-orders/**").hasAnyAuthority("MANAGER", "TECHNICIAN", "ADMIN")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
