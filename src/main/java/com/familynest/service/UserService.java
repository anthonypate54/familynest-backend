package com.familynest.service;

import com.familynest.model.User;
import com.familynest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    public Optional<User> findById(Long id) {
        logger.info("Finding user by ID: {}", id);
        return userRepository.findById(id);
    }
    
    public Optional<User> findByEmail(String email) {
        logger.info("Finding user by email: {}", email);
        return userRepository.findByEmail(email);
    }
    
    public User save(User user) {
        logger.info("Saving user: {}", user);
        return userRepository.save(user);
    }
    
    public boolean existsByEmail(String email) {
        logger.info("Checking if user exists by email: {}", email);
        return userRepository.existsByEmail(email);
    }
} 