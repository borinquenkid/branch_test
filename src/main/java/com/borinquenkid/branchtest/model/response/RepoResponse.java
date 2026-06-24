package com.borinquenkid.branchtest.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RepoResponse(@JsonProperty("name") String name, @JsonProperty("url") String url) {}
