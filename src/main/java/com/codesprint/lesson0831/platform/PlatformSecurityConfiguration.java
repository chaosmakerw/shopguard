package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

/** Self-hosted application login. Existing external-JWT production mode stays separate. */
@Configuration(proxyBeanMethods = false)
@Profile("platform")
public class PlatformSecurityConfiguration {
    @Bean public PasswordEncoder platformPasswordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public HttpSessionSecurityContextRepository platformSecurityContexts() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean public SecurityFilterChain platformSecurityFilterChain(HttpSecurity http,
            HttpSessionSecurityContextRepository contexts) throws Exception {
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .securityContext(context -> context.securityContextRepository(contexts))
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/platform", "/platform/**", "/trade", "/trade/**",
                    "/api/platform/session", "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/platform/login").permitAll()
                .requestMatchers("/api/demo/**", "/demo/**").denyAll()
                .requestMatchers("/api/assistant/**").denyAll()
                .requestMatchers("/api/platform/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"无操作权限或会话校验已过期，请刷新后重试\"}");
                }))
            .logout(logout -> logout.disable());
        return http.build();
    }
    @Bean public AuthenticatedPrincipalProvider platformPrincipalProvider() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof PlatformIdentity identity))
                return Optional.empty();
            return Optional.of(new AuthenticatedPrincipal(identity.id(), "ADMIN".equals(identity.role())));
        };
    }
    @Bean public CurrentUserContext platformCurrentUserContext(AuthenticatedPrincipalProvider provider) {
        return new ProviderCurrentUserContext(provider);
    }
}
