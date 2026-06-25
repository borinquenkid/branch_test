package com.borinquenkid.branchtest.mapper;

import com.borinquenkid.branchtest.model.github.GitHubRepo;
import com.borinquenkid.branchtest.model.github.GitHubUser;
import com.borinquenkid.branchtest.model.response.RepoResponse;
import com.borinquenkid.branchtest.model.response.UserResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface GitHubMapper {

  @Mapping(source = "user.login", target = "userName")
  @Mapping(source = "user.name", target = "displayName")
  @Mapping(source = "user.avatarUrl", target = "avatar")
  @Mapping(source = "user.location", target = "geoLocation")
  @Mapping(source = "user.email", target = "email")
  @Mapping(source = "user.url", target = "url")
  @Mapping(source = "user.createdAt", target = "createdAt", qualifiedByName = "formatCreatedAt")
  @Mapping(source = "repos", target = "repos")
  UserResponse toUserResponse(GitHubUser user, List<GitHubRepo> repos);

  RepoResponse toRepoResponse(GitHubRepo repo);

  @Named("formatCreatedAt")
  default @Nullable String formatCreatedAt(@Nullable String isoDate) {
    if (isoDate == null) return null;
    return Instant.parse(isoDate)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.RFC_1123_DATE_TIME);
  }
}
