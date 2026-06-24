package com.borinquenkid.branchtest.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUser(
    @JsonProperty("login") String login,
    @JsonProperty("name") @Nullable String name,
    @JsonProperty("avatar_url") @Nullable String avatarUrl,
    @JsonProperty("location") @Nullable String location,
    @JsonProperty("email") @Nullable String email,
    @JsonProperty("url") String url,
    @JsonProperty("created_at") @Nullable String createdAt) {}
