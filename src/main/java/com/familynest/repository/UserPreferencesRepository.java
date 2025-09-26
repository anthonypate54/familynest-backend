package com.familynest.repository;

import com.familynest.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    
    Optional<UserPreferences> findByUserId(Long userId);
    
    @Query(value = "SELECT * FROM user_preferences WHERE user_id = :userId", nativeQuery = true)
    Optional<UserPreferences> findByUserIdNative(@Param("userId") Long userId);
    
    void deleteByUserId(Long userId);
}