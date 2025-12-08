package com.krielwus.webtracinganalysis.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserAccountRepository userAccountRepository;

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public boolean register(String username, String password) {
        if (username == null || username.trim().isEmpty()) return false;
        if (password == null || password.trim().isEmpty()) return false;
        if (userAccountRepository.findByUsername(username) != null) return false;
        String salt = RandomUtil.randomString(16);
        String hash = SecureUtil.sha256(salt + password);
        UserAccount ua = new UserAccount();
        ua.setUsername(username);
        ua.setSalt(salt);
        ua.setPasswordHash(hash);
        if ("admin".equalsIgnoreCase(username)) { ua.setRole("SUPER_ADMIN"); } else { ua.setRole("USER"); }
        userAccountRepository.save(ua);
        return true;
    }

    public boolean authenticate(String username, String password) {
        UserAccount ua = userAccountRepository.findByUsername(username);
        if (ua == null) return false;
        String hash = SecureUtil.sha256(ua.getSalt() + password);
        return hash.equals(ua.getPasswordHash());
    }

    public UserAccount findByUsername(String username) {
        if (username == null) return null;
        return userAccountRepository.findByUsername(username);
    }

    @Transactional
    public UserAccount create(String username, String password, String role) {
        if (username == null || username.trim().isEmpty()) return null;
        if (password == null || password.trim().isEmpty()) return null;
        if (userAccountRepository.findByUsername(username) != null) return null;
        String salt = RandomUtil.randomString(16);
        String hash = SecureUtil.sha256(salt + password);
        UserAccount ua = new UserAccount();
        ua.setUsername(username.trim());
        ua.setSalt(salt);
        ua.setPasswordHash(hash);
        if (role == null || role.trim().isEmpty()) role = "USER";
        ua.setRole(role);
        return userAccountRepository.save(ua);
    }

    @Transactional
    public UserAccount update(Long id, String username, String password, String role) {
        if (id == null) return null;
        UserAccount ua = userAccountRepository.findById(id).orElse(null);
        if (ua == null) return null;
        if (username != null && !username.trim().isEmpty()) ua.setUsername(username.trim());
        if (password != null && !password.trim().isEmpty()) {
            String salt = RandomUtil.randomString(16);
            String hash = SecureUtil.sha256(salt + password);
            ua.setSalt(salt);
            ua.setPasswordHash(hash);
        }
        if (role != null && !role.trim().isEmpty()) ua.setRole(role.trim());
        return userAccountRepository.save(ua);
    }

    @Transactional
    public boolean delete(Long id) {
        if (id == null) return false;
        UserAccount ua = userAccountRepository.findById(id).orElse(null);
        if (ua == null) return false;
        userAccountRepository.deleteById(id);
        return true;
    }
}
