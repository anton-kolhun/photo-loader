package com.photoloader.config.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.web.context.request.NativeWebRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

//@Component
@Slf4j
public class FacebookSignInAdapter implements SignInAdapter {
    private final Set<UserData> authorizedUsers;


    public FacebookSignInAdapter(ObjectMapper objectMapper, String authorizedUsersPath) {
        this.authorizedUsers = loadAuthorizedUsers(objectMapper, authorizedUsersPath);
    }

    @Override
    public String signIn(String localUserId, Connection<?> connection, NativeWebRequest request) {
        log.info("signed in as name = {}, id= {} ", connection.getDisplayName(), connection.getKey().getProviderUserId());
        if (!authorizedUsers.contains(new UserData(connection.getKey().getProviderUserId()))) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(connection, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_REGULAR"))));
        } else {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(connection, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
        return null;
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

    @Getter
    @Setter
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    private static class UserData {

        @EqualsAndHashCode.Include
        private String id;

        private String name;

        public UserData(String id) {
            this.id = id;
        }

        public UserData(String id, String name) {
            this.id = id;
            this.name = name;
        }


    }
}
