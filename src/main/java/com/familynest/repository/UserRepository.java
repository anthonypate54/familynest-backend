package com.familynest.repository;

import com.familynest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    Optional<User> findByEmail(String email);
    
    // Replace the direct familyId access with a query that joins through the UserFamilyMembership table
    @Query("SELECT u FROM User u JOIN u.familyMemberships m WHERE m.familyId = :familyId")
    List<User> findByFamilyId(@Param("familyId") Long familyId);
    
    // Find users who are members of a specific family
    @Query("SELECT u FROM User u JOIN u.familyMemberships m WHERE m.familyId = :familyId")
    List<User> findMembersOfFamily(@Param("familyId") Long familyId);
    
    // Find users who have created (own) a family
    @Query("SELECT u FROM User u WHERE u.ownedFamily IS NOT NULL")
    List<User> findFamilyOwners();
    
    boolean existsByEmail(String email);
}