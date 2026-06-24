package com.borinquenkid.branchtest.client;

import com.borinquenkid.branchtest.exception.GitHubApiException;
import com.borinquenkid.branchtest.exception.UserNotFoundException;
import com.borinquenkid.branchtest.model.github.GitHubRepo;
import com.borinquenkid.branchtest.model.github.GitHubUser;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public record FetchResult(GitHubUser user, List<GitHubRepo> repos) {}

    public FetchResult fetch(String username) {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            var userTask  = scope.fork(() -> fetchUser(username));
            var reposTask = scope.fork(() -> fetchRepos(username));
            scope.join();
            return new FetchResult(userTask.get(), reposTask.get());
        } catch (StructuredTaskScope.FailedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UserNotFoundException ex) throw ex;
            if (cause instanceof GitHubApiException ex) throw ex;
            throw new GitHubApiException("GitHub API call failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException("GitHub API call interrupted", e);
        }
    }

    private GitHubUser fetchUser(String username) {
        try {
            return restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUser.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(username);
        } catch (RestClientResponseException e) {
            throw new GitHubApiException("GitHub user API returned " + e.getStatusCode(), e);
        }
    }

    private List<GitHubRepo> fetchRepos(String username) {
        try {
            return restClient.get()
                    .uri("/users/{username}/repos", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException e) {
            throw new GitHubApiException("GitHub repos API returned " + e.getStatusCode(), e);
        }
    }
}
