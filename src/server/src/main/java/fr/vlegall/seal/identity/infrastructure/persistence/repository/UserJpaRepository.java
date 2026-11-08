package fr.vlegall.seal.identity.infrastructure.persistence.repository;

import fr.vlegall.seal.identity.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {}
