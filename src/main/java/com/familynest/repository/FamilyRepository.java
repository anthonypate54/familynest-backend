package com.familynest.repository;

import com.familynest.model.Family;
import com.familynest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    // Find family by creator user id
    @Query("SELECT f FROM Family f WHERE f.createdBy.id = :userId")
    Family findByCreatedById(@Param("userId") Long userId);
    
    // Find families by name
    @Query("SELECT f FROM Family f WHERE f.name = :name")
    List<Family> findByName(@Param("name") String name);
}

