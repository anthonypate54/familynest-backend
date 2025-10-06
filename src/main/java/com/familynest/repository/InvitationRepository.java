package com.familynest.repository;

import com.familynest.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByEmail(String email);
    
    List<Invitation> findByEmailAndFamilyIdAndStatus(String email, Long familyId, String status);
    
    Optional<Invitation> findByIdAndEmail(Long id, String email);
} 
