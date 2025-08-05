package com.familynest.repository;

import com.familynest.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    
    Optional<UserPreferences> findByUserId(Long userId);
    
    void deleteByUserId(Long userId);
}