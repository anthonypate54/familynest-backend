package com.familynest.model;

import java.time.LocalDateTime;

/**
 * Model class that encapsulates subscription purchase data from Google Play.
 * This provides a clean abstraction over the raw Google Play API response.
 */
public class SubscriptionPurchaseModel {
    
    private String purchaseToken;
    private String productId;
    private double price;
    private String linkedPurchaseToken;
    private String subscriptionState;
    private boolean isTrial;
    private LocalDateTime trialStartDate;
    private LocalDateTime trialEndDate;
    private LocalDateTime subscriptionEndDate;
    private boolean isAutoRenewing;
    private String offerId;
     
    // Constructors
    public SubscriptionPurchaseModel() {
        // Default constructor
    }
    
    // Getters and setters
    public String getPurchaseToken() {
        return purchaseToken;
    }
    
    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public String getLinkedPurchaseToken() {
        return linkedPurchaseToken;
    }
    
    public void setLinkedPurchaseToken(String linkedPurchaseToken) {
        this.linkedPurchaseToken = linkedPurchaseToken;
    }
    
    public String getSubscriptionState() {
        return subscriptionState;
    }
    
    public void setSubscriptionState(String subscriptionState) {
        this.subscriptionState = subscriptionState;
    }
    
    public boolean isTrial() {
        return isTrial;
    }
    
    public void setTrial(boolean isTrial) {
        this.isTrial = isTrial;
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
    
    public LocalDateTime getSubscriptionEndDate() {
        return subscriptionEndDate;
    }
    
    public void setSubscriptionEndDate(LocalDateTime subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }
    
    public boolean isAutoRenewing() {
        return isAutoRenewing;
    }
    
    public void setAutoRenewing(boolean isAutoRenewing) {
        this.isAutoRenewing = isAutoRenewing;
    }
    
    public String getOfferId() {
        return offerId;
    }
    
    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }
    
     
    @Override
    public String toString() {
        return "SubscriptionPurchaseModel{" +
                "purchaseToken='" + purchaseToken + '\'' +
                ", productId='" + productId + '\'' +
                ", price=" + price +
                ", linkedPurchaseToken='" + linkedPurchaseToken + '\'' +
                ", subscriptionState='" + subscriptionState + '\'' +
                ", isTrial=" + isTrial +
                ", subscriptionEndDate=" + subscriptionEndDate +
                ", isAutoRenewing=" + isAutoRenewing +
                 '}';
    }
}
