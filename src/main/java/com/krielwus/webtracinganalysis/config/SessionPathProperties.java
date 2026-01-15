package com.krielwus.webtracinganalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "tracing.session-path")
public class SessionPathProperties {
    private boolean collapseConsecutiveDuplicates = true;
    private long minStayMs = 0;
    private int maxDepth = 20;
    private int defaultLimitSessions = 100;
    private List<String> ignoreRoutePatterns = new ArrayList<>();

    public boolean isCollapseConsecutiveDuplicates() { return collapseConsecutiveDuplicates; }
    public void setCollapseConsecutiveDuplicates(boolean collapseConsecutiveDuplicates) { this.collapseConsecutiveDuplicates = collapseConsecutiveDuplicates; }
    public long getMinStayMs() { return minStayMs; }
    public void setMinStayMs(long minStayMs) { this.minStayMs = minStayMs; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getDefaultLimitSessions() { return defaultLimitSessions; }
    public void setDefaultLimitSessions(int defaultLimitSessions) { this.defaultLimitSessions = defaultLimitSessions; }
    public List<String> getIgnoreRoutePatterns() { return ignoreRoutePatterns; }
    public void setIgnoreRoutePatterns(List<String> ignoreRoutePatterns) { this.ignoreRoutePatterns = ignoreRoutePatterns; }
}
