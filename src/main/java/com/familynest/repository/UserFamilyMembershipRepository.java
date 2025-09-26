package com.familynest.repository;

import com.familynest.model.UserFamilyMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFamilyMembershipRepository extends JpaRepository<UserFamilyMembership, Long> {
    
    // Find all memberships for a user
    List<UserFamilyMembership> findByUserId(Long userId);
    
    // Find all users who are members of a family
    List<UserFamilyMembership> findByFamilyId(Long familyId);
    
    // Find specific membership
    Optional<UserFamilyMembership> findByUserIdAndFamilyId(Long userId, Long familyId);
    
    // Check if a user is a member of a family
    boolean existsByUserIdAndFamilyId(Long userId, Long familyId);
    
    // Find the active family membership for a user
    Optional<UserFamilyMembership> findByUserIdAndIsActiveTrue(Long userId);
    
    // Method to directly get a user's active family IDs
    @Query("SELECT m.familyId FROM UserFamilyMembership m WHERE m.userId = :userId AND m.isActive = true")
    List<Long> findActiveFamilyIdByUserId(@Param("userId") Long userId);
    
    // Custom query to get all families for a user with details
    @Query("SELECT m FROM UserFamilyMembership m JOIN Family f ON m.familyId = f.id WHERE m.userId = :userId")
    List<UserFamilyMembership> findUserFamiliesWithDetails(@Param("userId") Long userId);

    // Add the findFamilyIdsByUserId method to the UserFamilyMembershipRepository interface
    // This method will return just the family IDs without loading the entire entities
    @Query("SELECT m.familyId FROM UserFamilyMembership m WHERE m.userId = :userId")
    List<Long> findFamilyIdsByUserId(@Param("userId") Long userId);
}