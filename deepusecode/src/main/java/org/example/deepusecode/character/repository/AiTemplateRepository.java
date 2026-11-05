package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.AiTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiTemplateRepository extends JpaRepository<AiTemplate, Long> {

    Optional<AiTemplate> findByIdAndDeleted(Long id, Integer deleted);

    Optional<AiTemplate> findByIdAndOwnerIdAndDeleted(Long id, Long ownerId, Integer deleted);

    Page<AiTemplate> findByOwnerIdAndDeleted(Long ownerId, Integer deleted, Pageable pageable);

    Page<AiTemplate> findByVisibilityAndDeleted(Integer visibility, Integer deleted, Pageable pageable);

    Page<AiTemplate> findByVisibilityAndDeletedAndNameContaining(
            Integer visibility, Integer deleted, String keyword, Pageable pageable);

    long countByOwnerIdAndDeleted(Long ownerId, Integer deleted);

    long countBySetId(Long setId);
}
