package com.nirmaanwatch.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nirmaanwatch")
public class AppProperties {

    private String corsAllowedOrigins = "http://localhost:5173";
    private boolean seedDemo = true;
    private String demoPassword = "ChangeMe_Demo1!";
    private boolean anonymousReportsEnabled = true;
    private int loginMaxAttempts = 8;
    private int loginWindowSeconds = 900;
    private String aiProvider = "none";
    private final Evidence evidence = new Evidence();

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public boolean isSeedDemo() {
        return seedDemo;
    }

    public void setSeedDemo(boolean seedDemo) {
        this.seedDemo = seedDemo;
    }

    public String getDemoPassword() {
        return demoPassword;
    }

    public void setDemoPassword(String demoPassword) {
        this.demoPassword = demoPassword;
    }

    public boolean isAnonymousReportsEnabled() {
        return anonymousReportsEnabled;
    }

    public void setAnonymousReportsEnabled(boolean anonymousReportsEnabled) {
        this.anonymousReportsEnabled = anonymousReportsEnabled;
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public void setLoginMaxAttempts(int loginMaxAttempts) {
        this.loginMaxAttempts = loginMaxAttempts;
    }

    public int getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(int loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public static class Evidence {
        private String dir = "../evidence-storage";
        private long maxBytes = 5_242_880L;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }
}
