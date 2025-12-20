package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    UserAccount findByUsername(String username);
    
    List<UserAccount> findByRoleNot(String role);
}

