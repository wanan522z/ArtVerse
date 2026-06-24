package com.artverse.application;

import com.artverse.common.BusinessException;
import com.artverse.config.BCryptPasswordEncoder;
import com.artverse.domain.Role;
import com.artverse.domain.User;
import com.artverse.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks
    private AuthService authService;

    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "SecurePass1!";
    private static final String HASH = "$2a$10$hashed";

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("valid input creates user with USER role")
        void validInput() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            User saved = new User();
            saved.setId(1L);
            saved.setUsername(USERNAME);
            saved.setEmail(EMAIL);
            saved.setPasswordHash(HASH);
            saved.setRole(Role.USER);
            when(userRepository.save(any(User.class))).thenReturn(saved);

            User result = authService.register(USERNAME, EMAIL, PASSWORD);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo(USERNAME);
            assertThat(result.getRole()).isEqualTo(Role.USER);
            verify(passwordEncoder).encode(PASSWORD);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("password too short (7 chars)")
        void passwordTooShort() {
            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, "Ab1!567"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("password only letters")
        void passwordOnlyLetters() {
            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, "abcdefgh"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("password only digits")
        void passwordOnlyDigits() {
            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, "12345678"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("password blank")
        void passwordBlank() {
            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, "   "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("password null")
        void passwordNull() {
            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("duplicate username")
        void duplicateUsername() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(409);
        }

        @Test
        @DisplayName("duplicate email")
        void duplicateEmail() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(USERNAME, EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(409);
        }

        @Test
        @DisplayName("invalid email format")
        void invalidEmail() {
            assertThatThrownBy(() -> authService.register(USERNAME, "not-an-email", PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("username too short")
        void usernameTooShort() {
            assertThatThrownBy(() -> authService.register("a", EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }

        @Test
        @DisplayName("username blank")
        void usernameBlank() {
            assertThatThrownBy(() -> authService.register("  ", EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .extracting("status").isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        private User user;

        @BeforeEach
        void setUp() {
            user = new User();
            user.setId(1L);
            user.setUsername(USERNAME);
            user.setPasswordHash(HASH);
        }

        @Test
        @DisplayName("valid credentials return user")
        void validCredentials() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

            User result = authService.login(USERNAME, PASSWORD);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("wrong password throws 401 with generic message")
        void wrongPassword() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(USERNAME, "wrong"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getStatus()).isEqualTo(401);
                        assertThat(be.getMessage()).contains("用户名或密码错误");
                    });
        }

        @Test
        @DisplayName("nonexistent user throws 401 with same generic message as wrong password")
        void nonexistentUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("ghost", PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getStatus()).isEqualTo(401);
                        assertThat(be.getMessage()).contains("用户名或密码错误");
                    });
        }
    }
}
