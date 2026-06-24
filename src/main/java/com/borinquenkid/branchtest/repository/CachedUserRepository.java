package com.borinquenkid.branchtest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CachedUserRepository extends JpaRepository<CachedUser, String> {

    @Query("SELECT c FROM CachedUser c WHERE c.username = :username AND c.cachedAt >= :cutoff")
    Optional<CachedUser> findFreshByUsername(String username, OffsetDateTime cutoff);
}
