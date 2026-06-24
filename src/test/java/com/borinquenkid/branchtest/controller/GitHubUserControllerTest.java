package com.borinquenkid.branchtest.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.borinquenkid.branchtest.exception.GitHubApiException;
import com.borinquenkid.branchtest.exception.GlobalExceptionHandler;
import com.borinquenkid.branchtest.exception.UserNotFoundException;
import com.borinquenkid.branchtest.model.response.RepoResponse;
import com.borinquenkid.branchtest.model.response.UserResponse;
import com.borinquenkid.branchtest.service.GitHubUserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GitHubUserControllerTest {

  private MockMvc mockMvc;
  private final GitHubUserService service = mock(GitHubUserService.class);

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new GitHubUserController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void happyPathReturnsCorrectJsonShape() throws Exception {
    var response =
        new UserResponse(
            "octocat",
            "The Octocat",
            "https://avatars.githubusercontent.com/u/583231?v=4",
            "San Francisco",
            null,
            "https://api.github.com/users/octocat",
            "Tue, 25 Jan 2011 18:44:36 GMT",
            List.of(
                new RepoResponse(
                    "Hello-World", "https://api.github.com/repos/octocat/Hello-World")));
    when(service.getUser("octocat")).thenReturn(response);

    mockMvc
        .perform(get("/users/octocat"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user_name").value("octocat"))
        .andExpect(jsonPath("$.display_name").value("The Octocat"))
        .andExpect(jsonPath("$.avatar").value("https://avatars.githubusercontent.com/u/583231?v=4"))
        .andExpect(jsonPath("$.geo_location").value("San Francisco"))
        .andExpect(jsonPath("$.created_at").value("Tue, 25 Jan 2011 18:44:36 GMT"))
        .andExpect(jsonPath("$.repos[0].name").value("Hello-World"));
  }

  @Test
  void unknownUserReturns404() throws Exception {
    when(service.getUser("ghost")).thenThrow(new UserNotFoundException("ghost"));

    mockMvc.perform(get("/users/ghost")).andExpect(status().isNotFound());
  }

  @Test
  void gitHubApiErrorReturns502() throws Exception {
    when(service.getUser("octocat"))
        .thenThrow(new GitHubApiException("upstream error", new RuntimeException()));

    mockMvc.perform(get("/users/octocat")).andExpect(status().isBadGateway());
  }

  @Test
  void blankUsernameReturns400() throws Exception {
    mockMvc.perform(get("/users/{username}", " ")).andExpect(status().isBadRequest());
  }
}
