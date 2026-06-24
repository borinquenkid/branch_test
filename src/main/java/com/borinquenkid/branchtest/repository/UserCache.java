package com.borinquenkid.branchtest.repository;

import com.borinquenkid.branchtest.config.GitHubProperties;
import com.borinquenkid.branchtest.model.response.UserResponse;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class UserCache {

    private final CachedUserRepository repository;
    private final GitHubProperties properties;

    public UserCache(CachedUserRepository repository, GitHubProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public Optional<UserResponse> findFresh(String username) {
        var cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusHours(properties.cache().l2TtlHours());
        return repository.findFreshByUsername(username, cutoff)
                .map(CachedUser::getResponse);
    }

    public void put(String username, UserResponse response) {
        repository.save(new CachedUser(username, response, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
