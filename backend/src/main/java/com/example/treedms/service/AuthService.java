package com.example.treedms.service;

import com.example.treedms.config.AuthProperties;
import com.example.treedms.dto.LoginRequest;
import com.example.treedms.dto.LoginResponse;
import com.example.treedms.dto.SessionUser;
import com.example.treedms.dto.UserRole;
import com.example.treedms.exception.AppException;
import com.example.treedms.support.UserContext;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();

    public AuthService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public LoginResponse login(LoginRequest request) {
        SessionUser user = authenticate(request);
        String token = newToken();
        sessions.put(token, user);
        return new LoginResponse(token, user.username(), user.role().name());
    }

    public Optional<SessionUser> findByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token));
    }

    public SessionUser currentUser() {
        SessionUser user = UserContext.get();
        if (user == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public void requireAdmin() {
        if (!currentUser().isAdmin()) {
            throw new AppException(HttpStatus.FORBIDDEN, "当前账号没有管理员权限");
        }
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
    }

    private SessionUser authenticate(LoginRequest request) {
        if (matches(request, authProperties.getAdmin())) {
            return new SessionUser(authProperties.getAdmin().getUsername(), UserRole.ADMIN);
        }
        if (matches(request, authProperties.getVisitor())) {
            return new SessionUser(authProperties.getVisitor().getUsername(), UserRole.VISITOR);
        }
        throw new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    private boolean matches(LoginRequest request, AuthProperties.Account account) {
        return account.getUsername().equals(request.username()) && account.getPassword().equals(request.password());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
