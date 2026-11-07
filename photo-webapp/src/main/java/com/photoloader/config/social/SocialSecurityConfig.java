package com.photoloader.config.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@PropertySource("classpath:social-config.properties")
@ConditionalOnProperty(name = "security.authorization", havingValue = "social")
@Slf4j
public class SocialSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OidcUserService customOidcUserService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login*", "/signin/**", "/signup/**", "/policy",
                                "/js/**", "/css/**", "/img/**", "/actuator/health", "/favicon.png", "/login_image.jpg").permitAll()
                        .anyRequest().hasAnyRole("ADMIN", "PUBLIC")
                )
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login").permitAll()
                        .authorizationEndpoint(authEndpoint ->
                                authEndpoint.baseUri("/login/oauth2/authorization"))
                        .userInfoEndpoint(userInfo ->
                                userInfo.oidcUserService(customOidcUserService))
                );
        return http.build();
    }

    private Set<UserData> loadAuthorizedUsers(ObjectMapper objectMapper, String authorizedUsersPath) {
        Path path = Paths.get(authorizedUsersPath);
        if (!Files.exists(path)) {
            log.warn("Authorized users file not found at: {}. Using empty authorized users list.", authorizedUsersPath);
            return Collections.emptySet();
        }
        try {
            byte[] usersBinary = Files.readAllBytes(path);
            UserData[] authorizedUsers = objectMapper.readValue(usersBinary, UserData[].class);
            return new HashSet<>(Arrays.asList(authorizedUsers));
        } catch (IOException e) {
            throw new RuntimeException("error occurred while loading users from file: " + authorizedUsersPath, e);
        }
    }

    @Bean
    public OidcUserService customOidcUserService(ObjectMapper objectMapper,
                                                 @Value("${authorizedUsers.google.path}") String authorizedUsersPath,
                                                 RestTemplate restTemplate, @Value("${ntfy.url}") String ntfyUrl) {
        return new CustomOidcUserService(loadAuthorizedUsers(objectMapper, authorizedUsersPath), restTemplate, ntfyUrl);
    }

    @Slf4j
    public static class CustomOidcUserService extends OidcUserService {

        private final Set<UserData> authorizedUsers;
        private final RestTemplate restTemplate;
        private final String ntfyUrl;

        public CustomOidcUserService(Set<UserData> authorizedUsers, RestTemplate restTemplate, String ntfyUrl) {
            this.authorizedUsers = authorizedUsers;
            this.restTemplate = restTemplate;
            this.ntfyUrl = ntfyUrl;
        }

        @Override
        public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
            OidcUser user = super.loadUser(userRequest);
            logSignIn(user);
            List<GrantedAuthority> extendedAuth = new ArrayList<>(user.getAuthorities());
            if (user.getEmail() != null && authorizedUsers.contains(new UserData(user.getEmail()))) {
                extendedAuth.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            } else {
                extendedAuth.add(new SimpleGrantedAuthority("ROLE_PUBLIC"));
            }
            return new DefaultOidcUser(extendedAuth, user.getIdToken());
        }

        private void logSignIn(OidcUser user) {
            String msg = String.format("signed in via google as email = %s", user.getEmail());
            log.info(msg);
            try {
                HttpEntity<String> requestEntity = new HttpEntity<>(msg);
                String response = restTemplate.postForObject(ntfyUrl, requestEntity, String.class);
                log.info("ntfy response: {}", response);
            } catch (Exception e) {
                log.warn("Could not send msg to ntfy.sh", e);
            }
        }
    }

    @Getter
    @Setter
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @NoArgsConstructor
    public static class UserData {

        @EqualsAndHashCode.Include
        private String email;

        private String name;

        public UserData(String email) {
            this.email = email;
        }
    }
}
