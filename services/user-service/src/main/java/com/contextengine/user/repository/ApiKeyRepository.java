
package com.contextengine.user.repository;

import com.contextengine.user.model.ApiKey;
import com.contextengine.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUser(User user);

    boolean existsByUserAndName(User user, String name);
}
