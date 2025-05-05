package com.mylifeapp.controller;

import com.mylifeapp.config.UserRole;
import com.mylifeapp.model.User;
import com.mylifeapp.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegisterController {

    private final UserService userService;

    public RegisterController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestParam String username, @RequestParam String password, @RequestParam UserRole role) {
        User user = userService.registerUser(username, password, role);
        return ResponseEntity.ok(user);
    }
}