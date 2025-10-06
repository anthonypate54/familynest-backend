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
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Table(name = "app_user")
@JsonIdentityInfo(
  generator = ObjectIdGenerators.PropertyGenerator.class, 
  property = "id")
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
    @JsonBackReference
    private Family ownedFamily;

    // Many families that the user is a member of
    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
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
    
    // showDemographics field removed - now handled by user_preferences table

    // Subscription and trial fields
    private String subscriptionStatus = "trial";
    
    private LocalDateTime trialStartDate;
    
    private LocalDateTime trialEndDate;
    
    private LocalDateTime subscriptionStartDate;
    
    private LocalDateTime subscriptionEndDate;
    
    private String paymentMethod;
    
    private String stripeCustomerId;
    
    private String stripeSubscriptionId;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal monthlyPrice = new BigDecimal("4.99");
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    // Password reset fields
    private String passwordResetToken;
    
    private LocalDateTime passwordResetTokenExpiresAt;
    
    private LocalDateTime passwordResetRequestedAt;

    // Onboarding state tracking using bitmap
    // Bit 0 (1): Has messages, Bit 1 (2): Has DMs, Bit 2 (4): Has family membership, Bit 3 (8): Has pending invitations
    private Integer onboardingState = 0;
    
    // Session management for single device enforcement
    private String currentSessionId;

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
        throw new UnsupportedOperationException("This method is no longer supported - use userFamilyMembershipRepository.findActiveFamilyIdByUserId(userId) instead");
    }
    
    // Method to set active family ID (for backward compatibility)
    public void setFamilyId(Long familyId) {
        throw new UnsupportedOperationException("This method is no longer supported - manage UserFamilyMembership relationships directly");
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

    // showDemographics getter/setter removed - now handled by user_preferences table

    // Subscription and trial getters and setters
    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public LocalDateTime getTrialStartDate() {
        return trialStartDate;
    }

    public void setTrialStartDate(LocalDateTime trialStartDate) {
        this.trialStartDate = trialStartDate;
    }

    public LocalDateTime getTrialEndDate() {
        return trialEndDate;
    }

    public void setTrialEndDate(LocalDateTime trialEndDate) {
        this.trialEndDate = trialEndDate;
    }

    public LocalDateTime getSubscriptionStartDate() {
        return subscriptionStartDate;
    }

    public void setSubscriptionStartDate(LocalDateTime subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }

    public LocalDateTime getSubscriptionEndDate() {
        return subscriptionEndDate;
    }

    public void setSubscriptionEndDate(LocalDateTime subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods for subscription logic
    public boolean isInTrial() {
        return "trial".equals(subscriptionStatus) && 
               trialEndDate != null && 
               trialEndDate.isAfter(LocalDateTime.now());
    }

    public boolean hasActiveSubscription() {
        return "active".equals(subscriptionStatus) ||
               ("trial".equals(subscriptionStatus) && isInTrial());
    }

    public long getDaysLeftInTrial() {
        if (trialEndDate == null) return 0;
        return java.time.Duration.between(LocalDateTime.now(), trialEndDate).toDays();
    }
    
    // Password reset getters and setters
    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    public void setPasswordResetToken(String passwordResetToken) {
        this.passwordResetToken = passwordResetToken;
    }

    public LocalDateTime getPasswordResetTokenExpiresAt() {
        return passwordResetTokenExpiresAt;
    }

    public void setPasswordResetTokenExpiresAt(LocalDateTime passwordResetTokenExpiresAt) {
        this.passwordResetTokenExpiresAt = passwordResetTokenExpiresAt;
    }

    public LocalDateTime getPasswordResetRequestedAt() {
        return passwordResetRequestedAt;
    }

    public void setPasswordResetRequestedAt(LocalDateTime passwordResetRequestedAt) {
        this.passwordResetRequestedAt = passwordResetRequestedAt;
    }
    
    // Helper methods for password reset
    public boolean isPasswordResetTokenValid() {
        return passwordResetToken != null && 
               passwordResetTokenExpiresAt != null && 
               passwordResetTokenExpiresAt.isAfter(LocalDateTime.now());
    }
    
    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetTokenExpiresAt = null;
        this.passwordResetRequestedAt = null;
    }
    
    // Onboarding state getters and setters
    public Integer getOnboardingState() {
        return onboardingState;
    }

    public void setOnboardingState(Integer onboardingState) {
        this.onboardingState = onboardingState;
    }
    
    /**
     * Get the current session ID for single device enforcement
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }
    
    /**
     * Set the current session ID for single device enforcement
     */
    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }
}
