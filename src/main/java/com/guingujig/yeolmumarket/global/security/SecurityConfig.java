package com.guingujig.yeolmumarket.global.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private static final RequestMatcher[] PUBLIC_REQUEST_MATCHERS = {
    pathPattern(HttpMethod.GET, "/"),
    pathPattern(HttpMethod.GET, "/index.html"),
    pathPattern(HttpMethod.GET, "/styles.css"),
    pathPattern(HttpMethod.GET, "/app.js"),
    pathPattern(HttpMethod.GET, "/api.js"),
    pathPattern(HttpMethod.GET, "/stomp-client.js"),
    pathPattern(HttpMethod.GET, "/assets/**"),
    pathPattern(HttpMethod.GET, "/favicon.ico"),
    pathPattern(HttpMethod.POST, "/api/auth/signup"),
    pathPattern(HttpMethod.POST, "/api/auth/login"),
    pathPattern(HttpMethod.POST, "/api/auth/refresh"),
    pathPattern("/ws"),
    pathPattern("/ws/**"),
    pathPattern(HttpMethod.GET, "/api/products"),
    pathPattern(HttpMethod.GET, "/api/products/*"),
    pathPattern(HttpMethod.GET, "/api/search/products"),
    pathPattern(HttpMethod.GET, "/api/search/v2/products"),
    pathPattern(HttpMethod.GET, "/api/search/popular-keywords"),
    pathPattern(HttpMethod.GET, "/api/categories"),
    pathPattern(HttpMethod.GET, "/api/categories/*/products"),
    pathPattern(HttpMethod.GET, "/api/users/*/products"),
    pathPattern(HttpMethod.GET, "/api/users/*"),
    pathPattern(HttpMethod.GET, "/api/users/*/reviews"),
    pathPattern(HttpMethod.GET, "/uploads/**")
  };

  private static final RequestMatcher[] AUTHENTICATED_USER_REQUEST_MATCHERS = {
    pathPattern(HttpMethod.GET, "/api/users/me/products"),
    pathPattern(HttpMethod.GET, "/api/users/me/orders"),
    pathPattern(HttpMethod.GET, "/api/users/me/sales"),
    pathPattern(HttpMethod.GET, "/api/users/me/reviews")
  };

  private static final RequestMatcher ADMIN_API_REQUEST_MATCHER = pathPattern("/api/admin/**");

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(AUTHENTICATED_USER_REQUEST_MATCHERS)
                    .authenticated()
                    .requestMatchers(PUBLIC_REQUEST_MATCHERS)
                    .permitAll()
                    .requestMatchers(ADMIN_API_REQUEST_MATCHER)
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
