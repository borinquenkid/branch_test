package com.borinquenkid.branchtest.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
public class RestClientConfig {

  @Bean
  public RestClient restClient(GitHubProperties props) {
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("Accept", "application/vnd.github+json")
        .build();
  }
}
