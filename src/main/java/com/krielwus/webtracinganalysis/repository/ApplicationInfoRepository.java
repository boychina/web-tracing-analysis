package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.ApplicationInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationInfoRepository extends JpaRepository<ApplicationInfo, Long> {
    ApplicationInfo findByAppCode(String appCode);
}

