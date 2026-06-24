package com.borinquenkid.branchtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.borinquenkid.branchtest.model.response.RepoResponse;
import com.borinquenkid.branchtest.model.response.UserResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

@JsonTest
class UserResponseConverterTest {

  @Autowired private ObjectMapper objectMapper;

  private UserResponseConverter converter;

  @BeforeEach
  void setUp() {
    converter = new UserResponseConverter(objectMapper);
  }

  private static final UserResponse RESPONSE =
      new UserResponse(
          "octocat",
          "The Octocat",
          "https://avatar.url",
          "San Francisco",
          null,
          "https://api.github.com/users/octocat",
          "Tue, 25 Jan 2011 18:44:36 GMT",
          List.of(
              new RepoResponse("Hello-World", "https://api.github.com/repos/octocat/Hello-World")));

  @Test
  void convertToDatabaseColumnProducesSnakeCaseJson() {
    var json = converter.convertToDatabaseColumn(RESPONSE);

    assertThat(json).contains("\"user_name\":\"octocat\"");
    assertThat(json).contains("\"display_name\":\"The Octocat\"");
    assertThat(json).contains("\"geo_location\":\"San Francisco\"");
  }

  @Test
  void convertToEntityAttributeRoundTrips() {
    var json = converter.convertToDatabaseColumn(RESPONSE);
    var result = converter.convertToEntityAttribute(json);

    assertThat(result).isEqualTo(RESPONSE);
  }
}
