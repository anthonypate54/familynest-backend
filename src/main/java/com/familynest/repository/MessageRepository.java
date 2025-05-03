package com.familynest.repository;

import com.familynest.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByFamilyId(Long familyId);
    List<Message> findByFamilyIdIn(List<Long> familyIds);
}