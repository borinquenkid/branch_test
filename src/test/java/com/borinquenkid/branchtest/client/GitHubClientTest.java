package com.borinquenkid.branchtest.client;

import com.borinquenkid.branchtest.exception.GitHubApiException;
import com.borinquenkid.branchtest.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitHubClientTest {

    private MockRestServiceServer server;
    private GitHubClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json");
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new GitHubClient(builder.build());
    }

    @Test
    void fetchReturnsBothDtos() {
        server.expect(requestTo("https://api.github.com/users/octocat")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"login":"octocat","name":"The Octocat",
                         "avatar_url":"https://avatars.githubusercontent.com/u/583231?v=4",
                         "location":"San Francisco","email":null,
                         "url":"https://api.github.com/users/octocat",
                         "created_at":"2011-01-25T18:44:36Z"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/users/octocat/repos")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"name":"Hello-World","url":"https://api.github.com/repos/octocat/Hello-World"}]
                        """, MediaType.APPLICATION_JSON));

        var result = client.fetch("octocat");

        assertThat(result.user().login()).isEqualTo("octocat");
        assertThat(result.repos()).hasSize(1);
        assertThat(result.repos().get(0).name()).isEqualTo("Hello-World");
    }

    @Test
    void throwsUserNotFoundExceptionOn404() {
        server.expect(requestTo("https://api.github.com/users/ghost")).andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.github.com/users/ghost/repos")).andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void throwsGitHubApiExceptionOn500() {
        server.expect(requestTo("https://api.github.com/users/octocat")).andExpect(method(GET))
                .andRespond(withServerError());
        server.expect(requestTo("https://api.github.com/users/octocat/repos")).andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch("octocat"))
                .isInstanceOf(GitHubApiException.class);
    }
}
