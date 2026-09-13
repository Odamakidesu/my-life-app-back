package com.mylifeapp.user.service;

import com.mylifeapp.user.model.UserRole;
import com.mylifeapp.user.model.User;
import com.mylifeapp.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // BCrypt

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String username, String rawPassword, UserRole userRole) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
//        user.setRole(UserRole.valueOf("USER"));
        user.setRole(userRole);
        return userRepository.save(user);
//        User user = new User();
//        user.setUsername(username);
//        String encodedPassword = passwordEncoder.encode(rawPassword);
//        User newUser = new User(null, username, encodedPassword, true);
//        return userRepository.save(newUser);
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
}