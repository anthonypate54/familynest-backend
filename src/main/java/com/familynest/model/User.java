package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    private String firstName;

    private String lastName;

    @Column(nullable = false)
    private String password;

    private String role;

    private String photo;
    
    // One family that the user owns (created)
    @OneToOne(mappedBy = "createdBy", fetch = FetchType.LAZY)
    private Family ownedFamily;

    // Many families that the user is a member of
    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserFamilyMembership> familyMemberships = new ArrayList<>();
    
    // Demographic information
    private String phoneNumber;
    
    private String address;
    
    private String city;
    
    private String state;
    
    private String zipCode;
    
    private String country;
    
    private LocalDate birthDate;
    
    @Column(columnDefinition = "TEXT")
    private String bio;
    
    private Boolean showDemographics = false;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    // New methods for family membership management
    public List<UserFamilyMembership> getFamilyMemberships() {
        return familyMemberships;
    }

    public void setFamilyMemberships(List<UserFamilyMembership> familyMemberships) {
        this.familyMemberships = familyMemberships;
    }
    
    // Method to get active family ID (for backward compatibility)
    public Long getFamilyId() {
        return familyMemberships.stream()
                .filter(UserFamilyMembership::isActive)
                .findFirst()
                .map(UserFamilyMembership::getFamilyId)
                .orElse(null);
    }
    
    // Method to set active family ID (for backward compatibility)
    public void setFamilyId(Long familyId) {
        // This is maintained for backward compatibility
        if (familyId == null) {
            return;
        }
        
        // Check if already a member of this family
        UserFamilyMembership existingMembership = familyMemberships.stream()
                .filter(m -> familyId.equals(m.getFamilyId()))
                .findFirst()
                .orElse(null);
                
        if (existingMembership != null) {
            // Set this membership as active
            familyMemberships.forEach(m -> m.setActive(false));
            existingMembership.setActive(true);
        } else {
            // Add new membership and set as active
            addFamilyMembership(familyId, true);
        }
    }
    
    // Get the family this user has created/owns (if any)
    public Family getOwnedFamily() {
        return ownedFamily;
    }
    
    public void setOwnedFamily(Family family) {
        this.ownedFamily = family;
    }
    
    // Helper method to add a new family membership
    public void addFamilyMembership(Long familyId, boolean isActive) {
        UserFamilyMembership membership = new UserFamilyMembership();
        membership.setUserId(this.id);
        membership.setFamilyId(familyId);
        membership.setActive(isActive);
        
        if (isActive) {
            // Make sure only one membership is active
            familyMemberships.forEach(m -> m.setActive(false));
        }
        
        // If this is the user's owned family, set role to ADMIN
        if (ownedFamily != null && ownedFamily.getId().equals(familyId)) {
            membership.setRole("ADMIN");
        } else {
            membership.setRole("MEMBER");
        }
        
        familyMemberships.add(membership);
    }
    
    // Demographic getters and setters
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Boolean getShowDemographics() {
        return showDemographics;
    }

    public void setShowDemographics(Boolean showDemographics) {
        this.showDemographics = showDemographics;
    }
}