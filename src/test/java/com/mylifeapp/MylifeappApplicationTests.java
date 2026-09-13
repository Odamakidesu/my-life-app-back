package com.mylifeapp;

import com.mylifeapp.user.repository.UserRepository;
import com.mylifeapp.auth.jwt.JwtAuthenticationFilter;
import com.mylifeapp.auth.userdetails.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "jwt.secret=REDACTED-SECRET-WAS-ROTATED",
        "jwt.expiration-ms=86400000",
        "spring.main.allow-bean-definition-overriding=true" // ★これを追加
})
@Import(MylifeappApplicationTests.MockedBeansConfig.class)
class MylifeappApplicationTests {

    @Test
    void contextLoads() {
        // Spring Boot context 起動確認
    }

    @TestConfiguration
    static class MockedBeansConfig {
        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
            return mock(JwtAuthenticationFilter.class);
        }

        @Bean
        public CustomUserDetailsService customUserDetailsService() {
            return mock(CustomUserDetailsService.class);
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}