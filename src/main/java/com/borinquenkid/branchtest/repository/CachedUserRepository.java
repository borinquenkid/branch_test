package com.borinquenkid.branchtest.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CachedUserRepository extends JpaRepository<CachedUser, String> {

  @Query("SELECT c FROM CachedUser c WHERE c.username = :username AND c.cachedAt >= :cutoff")
  Optional<CachedUser> findFreshByUsername(String username, OffsetDateTime cutoff);
}
