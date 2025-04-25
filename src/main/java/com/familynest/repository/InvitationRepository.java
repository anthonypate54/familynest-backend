package com.familynest.repository;

import com.familynest.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByInviteeEmail(String inviteeEmail);
    List<Invitation> findByFamilyId(Long familyId);
    Optional<Invitation> findByIdAndInviteeEmail(Long id, String inviteeEmail);
} 