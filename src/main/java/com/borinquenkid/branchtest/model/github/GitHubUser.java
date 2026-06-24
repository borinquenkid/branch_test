package com.borinquenkid.branchtest.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUser(
        @JsonProperty("login")      String login,
        @JsonProperty("name")       String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("location")   String location,
        @JsonProperty("email")      String email,
        @JsonProperty("url")        String url,
        @JsonProperty("created_at") String createdAt
) {}
