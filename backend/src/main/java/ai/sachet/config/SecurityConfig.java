package ai.sachet.config;

import ai.sachet.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security filter chain (Phase 2). Stateless JWT, locked-down defaults:
 * public = health probes + login only; everything else authenticated.
 * Failures render generic JSON (never stack traces or internals).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final SachetProperties props;
  private final JwtAuthFilter jwtFilter;
  private final ObjectMapper mapper;

  public SecurityConfig(SachetProperties props, JwtAuthFilter jwtFilter, ObjectMapper mapper) {
    this.props = props;
    this.jwtFilter = jwtFilter;
    this.mapper = mapper;
  }

  @Bean
  AuthenticationEntryPoint safeEntryPoint() {
    return (HttpServletRequest req, HttpServletResponse res,
        org.springframework.security.core.AuthenticationException e) -> write(res,
            HttpServletResponse.SC_UNAUTHORIZED, 401, "Authentication required.");
  }

  @Bean
  org.springframework.security.web.access.AccessDeniedHandler safeDeniedHandler() {
    return (req, res, e) -> write(res, HttpServletResponse.SC_FORBIDDEN, 403, "Forbidden.");
  }

  private void write(HttpServletResponse res, int status, int code, String message) throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    Map<String, Object> err = new HashMap<>();
    err.put("code", code);
    err.put("message", message);
    mapper.writeValue(res.getOutputStream(), Map.of("error", err));
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    String p = props.getApiPrefix();
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(c -> c.configurationSource(corsSource()))
        .exceptionHandling(e -> e
            .authenticationEntryPoint(safeEntryPoint())
            .accessDeniedHandler(safeDeniedHandler()))
        .authorizeHttpRequests(a -> a
            .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
            .requestMatchers(HttpMethod.GET, p + "/health", p + "/ready").permitAll()
            .requestMatchers(HttpMethod.POST, p + "/auth/login", p + "/auth/google").permitAll()
            .requestMatchers("/error").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsSource() {
    CorsConfiguration c = new CorsConfiguration();
    List<String> origins = props.getCorsOrigins();
    c.setAllowedOrigins(origins);
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
    c.setAllowCredentials(false);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
  }
}
