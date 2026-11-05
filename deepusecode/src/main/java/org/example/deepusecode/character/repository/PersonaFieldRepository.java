package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.PersonaField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonaFieldRepository extends JpaRepository<PersonaField, Long> {

    List<PersonaField> findBySheetIdOrderBySortOrderAscIdAsc(Long sheetId);

    void deleteBySheetId(Long sheetId);
}
