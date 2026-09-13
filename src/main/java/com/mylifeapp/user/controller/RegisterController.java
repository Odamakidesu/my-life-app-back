package com.mylifeapp.user.controller;

import com.mylifeapp.user.dto.RegisterRequest;
import com.mylifeapp.user.dto.UserResponse;
import com.mylifeapp.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class RegisterController {

    private final UserService userService;

    public RegisterController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = UserResponse.from(
                userService.registerUser(request.username(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
