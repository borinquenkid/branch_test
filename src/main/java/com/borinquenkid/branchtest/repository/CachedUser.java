package com.borinquenkid.branchtest.repository;

import com.borinquenkid.branchtest.model.response.UserResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "github_user_cache")
public class CachedUser {

    @Id
    private String username;

    @Column(nullable = false)
    @Convert(converter = UserResponseConverter.class)
    private UserResponse response;

    @Column(nullable = false)
    private OffsetDateTime cachedAt;

    protected CachedUser() {}

    public CachedUser(String username, UserResponse response, OffsetDateTime cachedAt) {
        this.username = username;
        this.response = response;
        this.cachedAt = cachedAt;
    }

    public String getUsername()         { return username; }
    public UserResponse getResponse()   { return response; }
    public OffsetDateTime getCachedAt() { return cachedAt; }
}
