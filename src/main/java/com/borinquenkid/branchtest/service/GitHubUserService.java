package com.borinquenkid.branchtest.service;

import com.borinquenkid.branchtest.client.GitHubClient;
import com.borinquenkid.branchtest.mapper.GitHubMapper;
import com.borinquenkid.branchtest.model.response.UserResponse;
import org.springframework.stereotype.Service;

@Service
public class GitHubUserService {

    private final GitHubClient client;
    private final GitHubMapper mapper;

    public GitHubUserService(GitHubClient client, GitHubMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public UserResponse getUser(String username) {
        var result = client.fetch(username);
        return mapper.toUserResponse(result.user(), result.repos());
    }
}
