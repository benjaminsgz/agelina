package com.yeven.thread.demo.auth.context;

import com.yeven.thread.demo.common.model.User;

public class LoginContext {

    private final String username;
    private final String password;
    private final User user;
    private final String token;

    public LoginContext(String username, String password, User user, String token) {
        this.username = username;
        this.password = password;
        this.user = user;
        this.token = token;
    }

    public static LoginContext init(String username, String password) {
        return new LoginContext(username, password, null, null);
    }

    public LoginContext withUser(User user) {
        return new LoginContext(username, password, user, token);
    }

    public LoginContext withToken(String token) {
        return new LoginContext(username, password, user, token);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }
}
