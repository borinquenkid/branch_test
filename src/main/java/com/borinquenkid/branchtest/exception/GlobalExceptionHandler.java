package com.borinquenkid.branchtest.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(UserNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNotFound(UserNotFoundException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(GitHubApiException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public Map<String, String> handleGitHubApiError(GitHubApiException ex) {
    return Map.of("error", ex.getMessage());
  }
}
