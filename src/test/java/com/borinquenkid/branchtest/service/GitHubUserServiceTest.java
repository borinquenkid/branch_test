package com.borinquenkid.branchtest.service;

import com.borinquenkid.branchtest.client.GitHubClient;
import com.borinquenkid.branchtest.client.GitHubClient.FetchResult;
import com.borinquenkid.branchtest.mapper.GitHubMapper;
import com.borinquenkid.branchtest.model.github.GitHubRepo;
import com.borinquenkid.branchtest.model.github.GitHubUser;
import com.borinquenkid.branchtest.model.response.RepoResponse;
import com.borinquenkid.branchtest.model.response.UserResponse;
import com.borinquenkid.branchtest.repository.UserCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubUserServiceTest {

    @Mock private GitHubClient client;
    @Mock private GitHubMapper mapper;
    @Mock private UserCache userCache;
    @InjectMocks private GitHubUserService service;

    @Test
    void l2MissFetchesFromApiAndStoresInCache() {
        var user = new GitHubUser("octocat", "The Octocat", "https://avatar.url",
                "San Francisco", null, "https://api.github.com/users/octocat", "2011-01-25T18:44:36Z");
        var repos = List.of(new GitHubRepo("Hello-World", "https://api.github.com/repos/octocat/Hello-World"));
        var expected = new UserResponse("octocat", "The Octocat", "https://avatar.url",
                "San Francisco", null, "https://api.github.com/users/octocat",
                "Tue, 25 Jan 2011 18:44:36 GMT",
                List.of(new RepoResponse("Hello-World", "https://api.github.com/repos/octocat/Hello-World")));

        when(userCache.findFresh("octocat")).thenReturn(Optional.empty());
        when(client.fetch("octocat")).thenReturn(new FetchResult(user, repos));
        when(mapper.toUserResponse(user, repos)).thenReturn(expected);

        var result = service.getUser("octocat");

        assertThat(result).isEqualTo(expected);
        verify(client).fetch("octocat");
        verify(userCache).put("octocat", expected);
    }

    @Test
    void l2HitReturnsCachedResponseWithoutCallingApi() {
        var expected = new UserResponse("octocat", "The Octocat", null, null, null,
                "https://api.github.com/users/octocat", null, List.of());

        when(userCache.findFresh("octocat")).thenReturn(Optional.of(expected));

        var result = service.getUser("octocat");

        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(client);
    }
}
