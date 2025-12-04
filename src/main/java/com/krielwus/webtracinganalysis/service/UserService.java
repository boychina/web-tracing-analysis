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
        userAccountRepository.save(ua);
        return true;
    }

    public boolean authenticate(String username, String password) {
        UserAccount ua = userAccountRepository.findByUsername(username);
        if (ua == null) return false;
        String hash = SecureUtil.sha256(ua.getSalt() + password);
        return hash.equals(ua.getPasswordHash());
    }
}

