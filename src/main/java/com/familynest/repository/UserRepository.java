package com.familynest.repository;

import com.familynest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    
    // Use LOWER to perform case-insensitive email lookup
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    
    // Replace the direct familyId access with a query that joins through the UserFamilyMembership table
    @Query(value = "SELECT u.* FROM app_user u JOIN user_family_membership m ON u.id = m.user_id WHERE m.family_id = :familyId", nativeQuery = true)
    List<User> findByFamilyId(@Param("familyId") Long familyId);
    
    // Find users who are members of a specific family - using native SQL to avoid lazy loading issues
    @Query(value = "SELECT u.* FROM app_user u JOIN user_family_membership m ON u.id = m.user_id WHERE m.family_id = :familyId", nativeQuery = true)
    List<User> findMembersOfFamily(@Param("familyId") Long familyId);
    
    // Find users who have created (own) a family
    @Query(value = "SELECT u.* FROM app_user u JOIN family f ON u.id = f.created_by WHERE f.created_by IS NOT NULL", nativeQuery = true)
    List<User> findFamilyOwners();
    
    // Also make this case-insensitive
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);
    
    // Trial expiry methods
    @Query(value = "SELECT * FROM app_user WHERE subscription_status = 'trial' AND trial_end_date < CURRENT_TIMESTAMP", nativeQuery = true)
    List<User> findExpiredTrials();
    
    @Query(value = "SELECT * FROM app_user WHERE subscription_status = 'trial' AND trial_end_date BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + INTERVAL '3 days'", nativeQuery = true)
    List<User> findTrialsExpiringSoon();
    
    // Password reset methods
    @Query("SELECT u FROM User u WHERE u.passwordResetToken = :token AND u.passwordResetTokenExpiresAt > CURRENT_TIMESTAMP")
    Optional<User> findByValidPasswordResetToken(@Param("token") String token);
    
    @Query("SELECT u FROM User u WHERE u.passwordResetToken IS NOT NULL AND u.passwordResetTokenExpiresAt < CURRENT_TIMESTAMP")
    List<User> findUsersWithExpiredPasswordResetTokens();
}