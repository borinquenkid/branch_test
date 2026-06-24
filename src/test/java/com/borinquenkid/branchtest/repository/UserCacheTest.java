package com.borinquenkid.branchtest.repository;

import com.borinquenkid.branchtest.config.GitHubProperties;
import com.borinquenkid.branchtest.model.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCacheTest {

    @Mock private CachedUserRepository repository;
    @Mock private GitHubProperties properties;
    @Mock private GitHubProperties.Cache cacheProps;
    @InjectMocks private UserCache userCache;

    private static final UserResponse RESPONSE =
            new UserResponse("octocat", null, null, null, null, null, null, List.of());

    @Test
    void findFreshReturnsMappedResponseWhenPresent() {
        var cached = new CachedUser("octocat", RESPONSE, OffsetDateTime.now());
        when(properties.cache()).thenReturn(cacheProps);
        when(cacheProps.l2TtlHours()).thenReturn(1);
        when(repository.findFreshByUsername(eq("octocat"), any())).thenReturn(Optional.of(cached));

        var result = userCache.findFresh("octocat");

        assertThat(result).contains(RESPONSE);
    }

    @Test
    void findFreshReturnsEmptyWhenNotPresent() {
        when(properties.cache()).thenReturn(cacheProps);
        when(cacheProps.l2TtlHours()).thenReturn(1);
        when(repository.findFreshByUsername(eq("octocat"), any())).thenReturn(Optional.empty());

        var result = userCache.findFresh("octocat");

        assertThat(result).isEmpty();
    }

    @Test
    void putSavesNewCachedUser() {
        userCache.put("octocat", RESPONSE);

        verify(repository).save(any(CachedUser.class));
    }
}
