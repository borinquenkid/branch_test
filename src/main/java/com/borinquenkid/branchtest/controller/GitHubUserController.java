package com.borinquenkid.branchtest.controller;

import com.borinquenkid.branchtest.model.response.UserResponse;
import com.borinquenkid.branchtest.service.GitHubUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
public class GitHubUserController {

  private final GitHubUserService service;

  public GitHubUserController(GitHubUserService service) {
    this.service = service;
  }

  @GetMapping("/{username}")
  public UserResponse getUser(@PathVariable String username) {
    if (username.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must not be blank");
    }
    return service.getUser(username);
  }
}
