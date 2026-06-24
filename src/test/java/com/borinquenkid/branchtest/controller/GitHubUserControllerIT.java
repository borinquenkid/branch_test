package com.borinquenkid.branchtest.controller;

import com.borinquenkid.branchtest.repository.CachedUserRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest
@ActiveProfiles("test")
class GitHubUserControllerIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final WireMockServer WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("github.base-url", () -> "http://localhost:" + WIREMOCK.port());
    }

    @AfterAll
    static void tearDown() {
        WIREMOCK.stop();
        POSTGRES.stop();
    }

    @Autowired private WebApplicationContext context;
    @Autowired private CacheManager cacheManager;
    @Autowired private CachedUserRepository repository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToApplicationContext(context).build();
        WIREMOCK.resetAll();
        cacheManager.getCache("github-users").clear();
        repository.deleteAll();
    }

    private static final String USER_JSON = """
            {"login":"octocat","name":"The Octocat",
             "avatar_url":"https://avatars.githubusercontent.com/u/583231?v=4",
             "location":"San Francisco","email":null,
             "url":"https://api.github.com/users/octocat",
             "created_at":"2011-01-25T18:44:36Z"}
            """;

    private static final String REPOS_JSON = """
            [{"name":"Hello-World","url":"https://api.github.com/repos/octocat/Hello-World"}]
            """;

    private void stubGitHub(String username) {
        WIREMOCK.stubFor(get("/users/" + username).willReturn(okJson(USER_JSON)));
        WIREMOCK.stubFor(get("/users/" + username + "/repos").willReturn(okJson(REPOS_JSON)));
    }

    @Test
    void happyPathReturnsCorrectJsonShape() {
        stubGitHub("octocat");

        client.get().uri("/users/octocat")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.user_name").isEqualTo("octocat")
                .jsonPath("$.display_name").isEqualTo("The Octocat")
                .jsonPath("$.geo_location").isEqualTo("San Francisco")
                .jsonPath("$.created_at").isEqualTo("Tue, 25 Jan 2011 18:44:36 GMT")
                .jsonPath("$.repos[0].name").isEqualTo("Hello-World");
    }

    @Test
    void unknownUserReturns404() {
        WIREMOCK.stubFor(get("/users/ghost").willReturn(aResponse().withStatus(404)));
        WIREMOCK.stubFor(get("/users/ghost/repos").willReturn(okJson("[]")));

        client.get().uri("/users/ghost")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void githubApiErrorReturns502() {
        WIREMOCK.stubFor(get("/users/broken").willReturn(aResponse().withStatus(500)));
        WIREMOCK.stubFor(get("/users/broken/repos").willReturn(aResponse().withStatus(500)));

        client.get().uri("/users/broken")
                .exchange()
                .expectStatus().isEqualTo(502);
    }

    @Test
    void l1CachePreventsSecondApiCall() {
        stubGitHub("octocat");

        client.get().uri("/users/octocat").exchange().expectStatus().isOk();
        client.get().uri("/users/octocat").exchange().expectStatus().isOk();

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/users/octocat")));
    }

    @Test
    void l2CacheServesResponseAfterL1Eviction() {
        stubGitHub("octocat");

        client.get().uri("/users/octocat").exchange().expectStatus().isOk();

        cacheManager.getCache("github-users").evict("octocat");

        client.get().uri("/users/octocat").exchange().expectStatus().isOk();

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/users/octocat")));
    }
}
