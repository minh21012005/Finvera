package com.minhnb.finvera_be.auth.config;

import com.minhnb.finvera_be.auth.filter.OwnerSessionExpiryFilter;
import com.minhnb.finvera_be.auth.service.LoginThrottle;
import com.minhnb.finvera_be.shared.api.CorrelationIdFilter;
import com.minhnb.finvera_be.shared.api.ProblemDetailsAdvice;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OwnerProperties.class)
public class OwnerSecurityConfiguration {

    @Bean
    SecurityFilterChain ownerSecurityFilterChain(
            HttpSecurity http,
            OwnerSessionExpiryFilter expiryFilter,
            CorrelationIdFilter correlationIdFilter,
            com.minhnb.finvera_be.research.config.InternalApiKeyFilter internalApiKeyFilter) throws Exception {
        var csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-CSRF-TOKEN");

        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/internal/v1/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/session").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/v1/**").permitAll()
                        .anyRequest().hasRole("OWNER"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemDetailsAdvice.write(response, request, 401,
                                        "AUTHENTICATION_REQUIRED", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemDetailsAdvice.write(response, request, 403,
                                        "ACCESS_DENIED", "Access denied")))
                .requestCache(cache -> cache.disable())
                .addFilterBefore(correlationIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(internalApiKeyFilter, AuthorizationFilter.class)
                .addFilterBefore(expiryFilter, AuthorizationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Bean
    PasswordEncoder ownerPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    LoginThrottle loginThrottle(Clock clock) {
        return new LoginThrottle(clock);
    }

    @Bean
    OwnerSessionExpiryFilter ownerSessionExpiryFilter(Clock clock) {
        return new OwnerSessionExpiryFilter(clock);
    }

    @Bean
    FilterRegistrationBean<OwnerSessionExpiryFilter> disableExpiryFilterAutoRegistration(
            OwnerSessionExpiryFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> disableCorrelationFilterAutoRegistration(
            CorrelationIdFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<com.minhnb.finvera_be.research.config.InternalApiKeyFilter> disableInternalApiKeyFilterAutoRegistration(
            com.minhnb.finvera_be.research.config.InternalApiKeyFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
