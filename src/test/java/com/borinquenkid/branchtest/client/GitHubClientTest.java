package com.borinquenkid.branchtest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.borinquenkid.branchtest.exception.GitHubApiException;
import com.borinquenkid.branchtest.exception.UserNotFoundException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubClientTest {

  private MockRestServiceServer server;
  private GitHubClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json");
    server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    client = new GitHubClient(builder.build());
  }

  private static final String USER_JSON =
      """
            {"login":"octocat","name":"The Octocat",
             "avatar_url":"https://avatars.githubusercontent.com/u/583231?v=4",
             "location":"San Francisco","email":null,
             "url":"https://api.github.com/users/octocat",
             "created_at":"2011-01-25T18:44:36Z"}
            """;

  @Test
  void fetchReturnsBothDtos() {
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                        [{"name":"Hello-World","url":"https://api.github.com/repos/octocat/Hello-World"}]
                        """,
                MediaType.APPLICATION_JSON));

    var result = client.fetch("octocat");

    assertThat(result.user().login()).isEqualTo("octocat");
    assertThat(result.repos()).hasSize(1);
    assertThat(result.repos().get(0).name()).isEqualTo("Hello-World");
  }

  @Test
  void throwsUserNotFoundExceptionOn404() {
    server
        .expect(requestTo("https://api.github.com/users/ghost"))
        .andExpect(method(GET))
        .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
    server
        .expect(requestTo("https://api.github.com/users/ghost/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch("ghost")).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void throwsGitHubApiExceptionOn500() {
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withServerError());
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch("octocat")).isInstanceOf(GitHubApiException.class);
  }

  @Test
  void fetchStopsAtLastPageWhenLinkHeaderHasNoNextRel() {
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                    "[{\"name\":\"only-repo\",\"url\":\"https://api.github.com/repos/octocat/only-repo\"}]",
                    MediaType.APPLICATION_JSON)
                .header(
                    "Link",
                    "<https://api.github.com/users/octocat/repos?page=1&per_page=100>; rel=\"first\""));

    var result = client.fetch("octocat");

    assertThat(result.repos()).hasSize(1);
  }

  @Test
  void throwsGitHubApiExceptionForNonHttpNetworkError() {
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withException(new IOException("connection reset")));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetch("octocat"))
        .isInstanceOf(GitHubApiException.class)
        .hasMessage("GitHub API call failed");
  }

  @Test
  void fetchCombinesAllPagesOfRepos() {
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                    "[{\"name\":\"repo-1\",\"url\":\"https://api.github.com/repos/octocat/repo-1\"}]",
                    MediaType.APPLICATION_JSON)
                .header(
                    "Link",
                    "<https://api.github.com/users/octocat/repos?page=2&per_page=100>; rel=\"next\""));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?page=2&per_page=100"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                "[{\"name\":\"repo-2\",\"url\":\"https://api.github.com/repos/octocat/repo-2\"}]",
                MediaType.APPLICATION_JSON));

    var result = client.fetch("octocat");

    assertThat(result.repos()).hasSize(2);
    assertThat(result.repos().get(0).name()).isEqualTo("repo-1");
    assertThat(result.repos().get(1).name()).isEqualTo("repo-2");
  }

  @Test
  void throwsGitHubApiExceptionWhenInterrupted() {
    // Per StructuredTaskScope.join() contract: "If the current thread's interrupt status
    // is set at the time of this call... throws InterruptedException."
    // Set up valid stubs so tasks succeed; the interrupt flag triggers the exception.
    server
        .expect(requestTo("https://api.github.com/users/octocat"))
        .andExpect(method(GET))
        .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://api.github.com/users/octocat/repos?per_page=100"))
        .andExpect(method(GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> client.fetch("octocat"))
          .isInstanceOf(GitHubApiException.class)
          .hasMessage("GitHub API call interrupted");
    } finally {
      Thread.interrupted(); // clear flag restored by the catch block
    }
  }
}
