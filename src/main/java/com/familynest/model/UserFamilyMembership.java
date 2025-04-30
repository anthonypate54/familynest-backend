package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_family_membership")
public class UserFamilyMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "family_id")
    private Long familyId;
    
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "family_id", insertable = false, updatable = false)
    private Family family;
    
    private boolean isActive; // Indicates if this is the active family for posting messages
    private LocalDateTime joinedAt;
    private String role; // e.g., "MEMBER", "ADMIN"
    
    // Constructor with required fields
    public UserFamilyMembership(Long userId, Long familyId, boolean isActive) {
        this.userId = userId;
        this.familyId = familyId;
        this.isActive = isActive;
        this.joinedAt = LocalDateTime.now();
        this.role = "MEMBER"; // Default role
    }
    
    // Default constructor for JPA
    public UserFamilyMembership() {
        this.joinedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
        if (user != null) {
            this.userId = user.getId();
        }
    }
    
    public Family getFamily() {
        return family;
    }
    
    public void setFamily(Family family) {
        this.family = family;
        if (family != null) {
            this.familyId = family.getId();
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
} 