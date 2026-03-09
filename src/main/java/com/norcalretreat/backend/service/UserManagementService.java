package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.AdminCreateUserRequest;
import com.norcalretreat.backend.dto.CreateUserResponse;
import com.norcalretreat.backend.dto.UserDTO;
import com.norcalretreat.backend.entity.Role;
import com.norcalretreat.backend.entity.User;
import com.norcalretreat.backend.repository.RefreshTokenRepository;
import com.norcalretreat.backend.repository.RoleRepository;
import com.norcalretreat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private EmailService emailService;

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDTO(user);
    }

    @Transactional
    public CreateUserResponse createUser(AdminCreateUserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (request.getLastName() == null || request.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }

        String username = request.getUsername() != null && !request.getUsername().isBlank()
                ? request.getUsername() : request.getEmail();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        String defaultPassword = "123456";

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPasswordChangeRequired(true);
        user.setIsActive(true);

        String roleName = request.getRoleName() != null ? request.getRoleName() : "MEMBER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        user.getRoles().add(role);

        user = userRepository.save(user);

        boolean emailSent = false;
        if (emailService != null) {
            try {
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName(), defaultPassword);
                emailSent = true;
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}", user.getEmail(), e);
            }
        }

        return new CreateUserResponse(toDTO(user), emailSent, defaultPassword);
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getIsActive() != null) user.setIsActive(dto.getIsActive());

        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsActive(false);
        userRepository.save(user);
    }

    @Transactional
    public UserDTO assignRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        user.getRoles().add(role);
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public UserDTO removeRole(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.getRoles().removeIf(r -> r.getName().equals(roleName));
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public UserDTO unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setIsLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockExpiry(null);
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public void forcePassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional
    public void forceLogout(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        refreshTokenRepository.revokeAllByUser(user);
    }

    public List<String> getAvailableRoles() {
        return roleRepository.findAll().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        dto.setIsActive(user.getIsActive());
        dto.setIsLocked(user.getIsLocked());
        dto.setLastLogin(user.getLastLogin());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setPasswordChangeRequired(user.getPasswordChangeRequired());
        return dto;
    }
}
