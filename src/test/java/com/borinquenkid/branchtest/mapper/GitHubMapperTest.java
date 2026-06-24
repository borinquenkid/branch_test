package com.borinquenkid.branchtest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.borinquenkid.branchtest.model.github.GitHubRepo;
import com.borinquenkid.branchtest.model.github.GitHubUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(GitHubMapperImpl.class)
class GitHubMapperTest {

  @Autowired private GitHubMapper mapper;

  private static final GitHubUser OCTOCAT =
      new GitHubUser(
          "octocat",
          "The Octocat",
          "https://avatars.githubusercontent.com/u/583231?v=4",
          "San Francisco",
          null,
          "https://api.github.com/users/octocat",
          "2011-01-25T18:44:36Z");

  private static final GitHubRepo HELLO_WORLD =
      new GitHubRepo("Hello-World", "https://api.github.com/repos/octocat/Hello-World");

  @Test
  void mapsUserAndRepoFields() {
    var response = mapper.toUserResponse(OCTOCAT, List.of(HELLO_WORLD));

    assertThat(response.userName()).isEqualTo("octocat");
    assertThat(response.displayName()).isEqualTo("The Octocat");
    assertThat(response.avatar()).isEqualTo("https://avatars.githubusercontent.com/u/583231?v=4");
    assertThat(response.geoLocation()).isEqualTo("San Francisco");
    assertThat(response.url()).isEqualTo("https://api.github.com/users/octocat");
    assertThat(response.repos()).hasSize(1);
    assertThat(response.repos().get(0).name()).isEqualTo("Hello-World");
    assertThat(response.repos().get(0).url())
        .isEqualTo("https://api.github.com/repos/octocat/Hello-World");
  }

  @Test
  void reformatsCreatedAtToRfc1123() {
    var response = mapper.toUserResponse(OCTOCAT, List.of());

    assertThat(response.createdAt()).isEqualTo("Tue, 25 Jan 2011 18:44:36 GMT");
  }

  @Test
  void nullEmailPassesThroughAsNull() {
    var response = mapper.toUserResponse(OCTOCAT, List.of());

    assertThat(response.email()).isNull();
  }

  @Test
  void nullCreatedAtPassesThroughAsNull() {
    var user =
        new GitHubUser(
            "octocat",
            "The Octocat",
            "https://avatars.githubusercontent.com/u/583231?v=4",
            "San Francisco",
            null,
            "https://api.github.com/users/octocat",
            null);

    assertThat(mapper.toUserResponse(user, List.of()).createdAt()).isNull();
  }
}
