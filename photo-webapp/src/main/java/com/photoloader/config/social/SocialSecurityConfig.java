package com.photoloader.config.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoloader.config.social.fb.FacebookConnectionSignup;
import com.photoloader.config.social.fb.FacebookSignInAdapter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.mem.InMemoryUsersConnectionRepository;
import org.springframework.social.connect.support.ConnectionFactoryRegistry;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.facebook.connect.FacebookConnectionFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@PropertySource("social-config.properties")
@ConditionalOnProperty(name = "security.authorization", havingValue = "social")
public class SocialSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private FacebookConnectionSignup facebookConnectionSignup;

//    @Override
//    protected void configure(final HttpSecurity http) throws Exception {
//        http
//                .csrf().disable()
//                .authorizeRequests()
//                .antMatchers("/login*", "/signin/**", "/signup/**", "/policy",
//                        "/js/*", "/css/*", "/img/*", "/actuator/health").permitAll()
//                .anyRequest().hasAnyRole("ADMIN", "PUBLIC")
//                //.anyRequest().authenticated()
//                .and()
//                .formLogin().loginPage("/login").permitAll()
//                .and()
//                .logout();
//    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/login*", "/signin/**", "/signup/**", "/policy",
                        "/js/*", "/css/*", "/img/*", "/actuator/health").permitAll()
                .anyRequest().hasAnyRole("ADMIN", "PUBLIC")
                //.anyRequest().authenticated()
                .and()
                .formLogin().loginPage("/login").permitAll()
                .and()
                .logout()
                .and()
                .oauth2Login()
                .loginPage("/login").permitAll()
                .authorizationEndpoint()
                .baseUri("/login/oauth2/authorization/");
    }

    @Bean
    public ProviderSignInController providerSignInController(@Value("${app.url}") String appUrl,
                                                             @Value("${spring.social.facebook.appSecret}") String appSecret,
                                                             @Value("${spring.social.facebook.appId}") String appId,
                                                             @Value("${authorizedUsers.path}") String authorizedUsersPath,
                                                             ObjectMapper objectMapper) {
        ConnectionFactoryLocator connectionFactoryLocator = connectionFactoryLocator(appSecret, appId);
        UsersConnectionRepository usersConnectionRepository = getUsersConnectionRepository(connectionFactoryLocator);
        ((InMemoryUsersConnectionRepository) usersConnectionRepository).setConnectionSignUp(facebookConnectionSignup);
        ProviderSignInController controller = new ProviderSignInController(connectionFactoryLocator,
                usersConnectionRepository, new FacebookSignInAdapter(objectMapper, authorizedUsersPath));
        controller.setApplicationUrl(appUrl);
        return controller;
    }

    private ConnectionFactoryLocator connectionFactoryLocator(String appSecret, String appId) {
        ConnectionFactoryRegistry registry = new ConnectionFactoryRegistry();
        registry.addConnectionFactory(new FacebookConnectionFactory(appId, appSecret));
        return registry;
    }

    private UsersConnectionRepository getUsersConnectionRepository(ConnectionFactoryLocator connectionFactoryLocator) {
        return new InMemoryUsersConnectionRepository(connectionFactoryLocator);
    }


    private Set<UserData> loadAuthorizedUsers(ObjectMapper objectMapper, String authorizedUsersPath) {
        try {
            byte[] usersBinary = Files.readAllBytes(Paths.get(authorizedUsersPath));
            UserData[] authorizedUsers = objectMapper.readValue(usersBinary, UserData[].class);
            return new HashSet<>(Arrays.asList(authorizedUsers));
        } catch (IOException e) {
            throw new RuntimeException("error occurred while loading users from file", e);
        }
    }

    @Bean
    public OidcUserService customService(ObjectMapper objectMapper,
                                         @Value("${authorizedUsers.google.path}") String authorizedUsersPath) {
        return new CustomOidcUserService(loadAuthorizedUsers(objectMapper, authorizedUsersPath));
    }

    @Slf4j
    public static class CustomOidcUserService extends OidcUserService {

        private final Set<UserData> authorizedUsers;

        public CustomOidcUserService(Set<UserData> authorizedUsers) {
            this.authorizedUsers = authorizedUsers;
        }

        @Override
        public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
            OidcUser user = super.loadUser(userRequest);
            log.info("signed in via google as email = {}", user.getEmail());
            List extendedAuth = new ArrayList<>(user.getAuthorities());
            if (authorizedUsers.contains(new UserData(user.getEmail()))) {
                extendedAuth.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            } else {
                extendedAuth.add(new SimpleGrantedAuthority("ROLE_PUBLIC"));
            }
            OidcUser updated = new DefaultOidcUser(extendedAuth, user.getIdToken());
            return updated;
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
