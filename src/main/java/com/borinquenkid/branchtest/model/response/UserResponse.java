package com.borinquenkid.branchtest.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.util.List;

public record UserResponse(
        @JsonProperty("user_name")                      String userName,
        @JsonProperty("display_name") @Nullable         String displayName,
        @JsonProperty("avatar")       @Nullable         String avatar,
        @JsonProperty("geo_location") @Nullable         String geoLocation,
        @JsonProperty("email")        @Nullable         String email,
        @JsonProperty("url")                            String url,
        @JsonProperty("created_at")   @Nullable         String createdAt,
        @JsonProperty("repos")                          List<RepoResponse> repos
) {}
