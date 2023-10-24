package com.photoloader.config.social.fb;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class FacebookSignInAdapter implements SignInAdapter {

    private final Set<UserData> authorizedUsers;

    private final RestTemplate restTemplate;

    private final String ntfyUrl;


    public FacebookSignInAdapter(ObjectMapper objectMapper, String authorizedUsersPath, RestTemplate restTemplate,
                                 String ntfyUrl) {
        this.authorizedUsers = loadAuthorizedUsers(objectMapper, authorizedUsersPath);
        this.restTemplate = restTemplate;
        this.ntfyUrl = ntfyUrl;
    }

    @Override
    public String signIn(String localUserId, Connection<?> connection, NativeWebRequest request) {
        logSignIn(connection);
        if (!authorizedUsers.contains(new UserData(connection.getKey().getProviderUserId()))) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(connection, null,
                            Arrays.asList(new SimpleGrantedAuthority("ROLE_PUBLIC"))));
        } else {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(connection, null,
                            Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
        return null;
    }

    private void logSignIn(Connection<?> connection) {
        String msg = String.format("signed in via fb as name = %s, id= %s ", connection.getDisplayName(),
                connection.getKey().getProviderUserId());
        log.info(msg);
        try {
            HttpEntity<String> requestEntity = new HttpEntity<>(msg);
            String response = restTemplate.postForObject(ntfyUrl, requestEntity, String.class);
            log.info("ntfy response: {}", response);
        } catch (Exception e) {
            log.warn("Could not send msg to ntfy.sh", e);
        }
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
    @NoArgsConstructor
    public static class UserData {

        @EqualsAndHashCode.Include
        private String id;

        private String name;

        public UserData(String id) {
            this.id = id;
        }

    }
}
