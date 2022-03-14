package com.photoloader.config.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.mem.InMemoryUsersConnectionRepository;
import org.springframework.social.connect.support.ConnectionFactoryRegistry;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.facebook.connect.FacebookConnectionFactory;

@Configuration
@EnableWebSecurity
@PropertySource("social-config.properties")
@ConditionalOnProperty(name = "security.authorization", havingValue = "facebook")
public class SocialSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private FacebookConnectionSignup facebookConnectionSignup;

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .antMatchers("/login*", "/signin/**", "/signup/**", "/policy",
                        "/js/*", "/css/*", "/img/*").permitAll()
                .anyRequest().hasRole("ADMIN")
                //.anyRequest().authenticated()
                .and()
                .formLogin().loginPage("/login").permitAll()
                .and()
                .logout();
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
}
