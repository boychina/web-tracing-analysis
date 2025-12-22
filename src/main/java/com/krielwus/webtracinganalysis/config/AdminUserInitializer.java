package com.krielwus.webtracinganalysis.config;

import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import com.krielwus.webtracinganalysis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保存在默认的超级管理员账户。
 */
@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final UserService userService;

    @Value("${app.init.admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.init.admin.password:admin}")
    private String defaultAdminPassword;

    public AdminUserInitializer(UserAccountRepository userAccountRepository, UserService userService) {
        this.userAccountRepository = userAccountRepository;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        if (defaultAdminUsername == null || defaultAdminUsername.trim().isEmpty()) {
            log.warn("Default admin username is empty, skip init");
            return;
        }
        UserAccount existing = userAccountRepository.findByUsername(defaultAdminUsername.trim());
        if (existing != null) {
            return;
        }
        UserAccount created = userService.create(defaultAdminUsername.trim(), defaultAdminPassword, "SUPER_ADMIN");
        if (created != null) {
            log.info("Created default admin user: {}", defaultAdminUsername);
        } else {
            log.warn("Failed to create default admin user: {}", defaultAdminUsername);
        }
    }
}
