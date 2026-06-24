package com.borinquenkid.branchtest.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepo(@JsonProperty("name") String name, @JsonProperty("url") String url) {}
