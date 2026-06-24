package com.borinquenkid.branchtest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String baseUrl, Cache cache) {
    public record Cache(int l2TtlHours) {}
}
