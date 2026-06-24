package com.borinquenkid.branchtest.client;

import com.borinquenkid.branchtest.exception.GitHubApiException;
import com.borinquenkid.branchtest.exception.UserNotFoundException;
import com.borinquenkid.branchtest.model.github.GitHubRepo;
import com.borinquenkid.branchtest.model.github.GitHubUser;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
            return Objects.requireNonNull(restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUser.class), "GitHub user API returned empty body");
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException(username);
        } catch (RestClientResponseException e) {
            throw new GitHubApiException("GitHub user API returned " + e.getStatusCode(), e);
        }
    }

    private static final ParameterizedTypeReference<List<GitHubRepo>> REPOS_TYPE =
            new ParameterizedTypeReference<>() {};

    private List<GitHubRepo> fetchRepos(String username) {
        try {
            List<GitHubRepo> all = new ArrayList<>();
            var page = restClient.get()
                    .uri("/users/{username}/repos?per_page=100", username)
                    .retrieve()
                    .toEntity(REPOS_TYPE);
            while (true) {
                if (page.getBody() != null) all.addAll(page.getBody());
                URI next = nextPageUri(page.getHeaders().getFirst("Link"));
                if (next == null) break;
                page = restClient.get().uri(next).retrieve().toEntity(REPOS_TYPE);
            }
            return all;
        } catch (RestClientResponseException e) {
            throw new GitHubApiException("GitHub repos API returned " + e.getStatusCode(), e);
        }
    }

    private static @Nullable URI nextPageUri(@Nullable String linkHeader) {
        if (linkHeader == null) return null;
        for (String segment : linkHeader.split(",")) {
            String[] parts = segment.trim().split(";", 2);
            if (parts.length == 2 && parts[1].trim().equals("rel=\"next\"")) {
                String url = parts[0].trim();
                return URI.create(url.substring(1, url.length() - 1));
            }
        }
        return null;
    }
}
