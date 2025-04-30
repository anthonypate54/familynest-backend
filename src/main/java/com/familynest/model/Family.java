package com.familynest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "family")
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @OneToOne
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;
    
    @OneToMany(mappedBy = "familyId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserFamilyMembership> members = new ArrayList<>();

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public User getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
    
    public List<UserFamilyMembership> getMembers() {
        return members;
    }
    
    public void setMembers(List<UserFamilyMembership> members) {
        this.members = members;
    }
    
    public void addMember(UserFamilyMembership membership) {
        this.members.add(membership);
    }
    
    public int getMemberCount() {
        return this.members.size();
    }
}