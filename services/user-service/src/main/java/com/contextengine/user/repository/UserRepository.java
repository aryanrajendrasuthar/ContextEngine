
package com.contextengine.user.repository;

import com.contextengine.user.model.Organization;
import com.contextengine.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByOrganization(Organization organization);

    long countByOrganization(Organization organization);
}
