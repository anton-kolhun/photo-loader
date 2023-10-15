package com.photoloader.config.social.fb;

import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.stereotype.Component;

@Component
public class FacebookConnectionSignup implements ConnectionSignUp {


    @Override
    public String execute(Connection<?> connection) {
        return connection.getDisplayName();
    }

}
