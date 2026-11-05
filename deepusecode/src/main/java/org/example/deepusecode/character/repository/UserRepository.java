package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccount(String account);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByAccount(String account);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
