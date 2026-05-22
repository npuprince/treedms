package com.example.treedms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private Account admin = new Account("admin", "admin123");
    private Account visitor = new Account("visitor", "visitor123");

    public Account getAdmin() {
        return admin;
    }

    public void setAdmin(Account admin) {
        this.admin = admin;
    }

    public Account getVisitor() {
        return visitor;
    }

    public void setVisitor(Account visitor) {
        this.visitor = visitor;
    }

    public static class Account {
        private String username;
        private String password;

        public Account() {
        }

        public Account(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
