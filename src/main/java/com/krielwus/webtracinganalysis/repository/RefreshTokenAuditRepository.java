package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.RefreshTokenAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefreshTokenAuditRepository extends JpaRepository<RefreshTokenAudit, Long> {
    List<RefreshTokenAudit> findByUserId(Long userId);
    List<RefreshTokenAudit> findByTokenId(Long tokenId);
}
