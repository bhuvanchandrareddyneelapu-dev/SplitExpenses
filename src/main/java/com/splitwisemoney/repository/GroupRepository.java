package com.splitwisemoney.repository;

import com.splitwisemoney.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Loads a Group together with its createdBy User in a single query,
     * preventing LazyInitializationException when the entity is accessed
     * outside a transaction boundary.
     */
    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.createdBy WHERE g.id = :id")
    Optional<Group> findByIdWithCreator(@Param("id") Long id);
}
