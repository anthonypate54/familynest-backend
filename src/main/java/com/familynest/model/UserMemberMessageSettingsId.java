package com.familynest.model;

import java.io.Serializable;
import java.util.Objects;

public class UserMemberMessageSettingsId implements Serializable {
    private Long userId;
    private Long familyId;
    private Long memberUserId;

    public UserMemberMessageSettingsId() {
    }

    public UserMemberMessageSettingsId(Long userId, Long familyId, Long memberUserId) {
        this.userId = userId;
        this.familyId = familyId;
        this.memberUserId = memberUserId;
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

    public Long getMemberUserId() {
        return memberUserId;
    }

    public void setMemberUserId(Long memberUserId) {
        this.memberUserId = memberUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMemberMessageSettingsId that = (UserMemberMessageSettingsId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(familyId, that.familyId) &&
               Objects.equals(memberUserId, that.memberUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, familyId, memberUserId);
    }
} 
