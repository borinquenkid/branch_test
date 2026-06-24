package com.borinquenkid.branchtest.service;

import com.borinquenkid.branchtest.client.GitHubClient;
import com.borinquenkid.branchtest.mapper.GitHubMapper;
import com.borinquenkid.branchtest.model.response.UserResponse;
import com.borinquenkid.branchtest.repository.UserCache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserService {

  private final GitHubClient client;
  private final GitHubMapper mapper;
  private final UserCache userCache;

  public GitHubUserService(GitHubClient client, GitHubMapper mapper, UserCache userCache) {
    this.client = client;
    this.mapper = mapper;
    this.userCache = userCache;
  }

  @Cacheable(cacheNames = "github-users", key = "#username")
  public UserResponse getUser(String username) {
    return userCache.findFresh(username).orElseGet(() -> fetchAndCache(username));
  }

  private UserResponse fetchAndCache(String username) {
    var result = client.fetch(username);
    var response = mapper.toUserResponse(result.user(), result.repos());
    userCache.put(username, response);
    return response;
  }
}
