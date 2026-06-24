package com.borinquenkid.branchtest.repository;

import com.borinquenkid.branchtest.model.response.UserResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CachedUserRepositoryTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("github.base-url", () -> "http://localhost:0");
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Autowired
    private CachedUserRepository repository;

    private static final UserResponse SAMPLE =
            new UserResponse("octocat", "The Octocat", null, null, null,
                    "https://api.github.com/users/octocat", null, List.of());

    @Test
    void savesAndRetrievesFreshEntry() {
        repository.save(new CachedUser("octocat", SAMPLE, OffsetDateTime.now(ZoneOffset.UTC)));

        var found = repository.findFreshByUsername("octocat", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("octocat");
        assertThat(found.get().getResponse().userName()).isEqualTo("octocat");
        assertThat(found.get().getCachedAt()).isNotNull();
    }

    @Test
    void returnsEmptyForStaleEntry() {
        repository.save(new CachedUser("stale", SAMPLE, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)));

        var found = repository.findFreshByUsername("stale", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));

        assertThat(found).isEmpty();
    }
}
