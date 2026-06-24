package com.borinquenkid.branchtest.repository;

import jakarta.persistence.Column;
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
    private String response;

    @Column(nullable = false)
    private OffsetDateTime cachedAt;

    protected CachedUser() {}

    public CachedUser(String username, String response, OffsetDateTime cachedAt) {
        this.username = username;
        this.response = response;
        this.cachedAt = cachedAt;
    }

    public String getUsername()  { return username; }
    public String getResponse()  { return response; }
    public OffsetDateTime getCachedAt() { return cachedAt; }

    public void setResponse(String response)   { this.response = response; }
    public void setCachedAt(OffsetDateTime cachedAt) { this.cachedAt = cachedAt; }
}
