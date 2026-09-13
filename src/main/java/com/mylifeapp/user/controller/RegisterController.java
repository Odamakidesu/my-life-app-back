package com.mylifeapp.user.controller;

import com.mylifeapp.user.model.UserRole;
import com.mylifeapp.user.model.User;
import com.mylifeapp.user.service.UserService;
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