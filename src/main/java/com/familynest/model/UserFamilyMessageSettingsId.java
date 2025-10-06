package com.familynest.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key class for UserFamilyMessageSettings.
 * Represents the user_id + family_id composite primary key.
 */
public class UserFamilyMessageSettingsId implements Serializable {
    private Long userId;
    private Long familyId;
    
    // Default constructor
    public UserFamilyMessageSettingsId() {
    }
    
    // Constructor with fields
    public UserFamilyMessageSettingsId(Long userId, Long familyId) {
        this.userId = userId;
        this.familyId = familyId;
    }
    
    // Getters and setters
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Long getFamilyId() {
        return familyId;
    }
    
    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }
    
    // Equals and hashCode are required for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserFamilyMessageSettingsId that = (UserFamilyMessageSettingsId) o;
        return Objects.equals(userId, that.userId) && 
               Objects.equals(familyId, that.familyId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, familyId);
    }
} 
