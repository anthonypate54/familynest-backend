package com.familynest.repository;

import com.familynest.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByFamilyId(Long familyId);
    List<Message> findByFamilyIdIn(List<Long> familyIds);
    
    /**
     * Find messages by family IDs with pagination
     */
    @Query("SELECT m FROM Message m WHERE m.familyId IN :familyIds ORDER BY m.timestamp DESC")
    List<Message> findByFamilyIdInPaged(@Param("familyIds") List<Long> familyIds, Pageable pageable);
}
