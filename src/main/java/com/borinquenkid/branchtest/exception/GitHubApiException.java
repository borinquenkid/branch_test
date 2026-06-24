package com.borinquenkid.branchtest.exception;

import org.springframework.lang.Nullable;

public class GitHubApiException extends RuntimeException {
  public GitHubApiException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
