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

        // Use the admin-provided password when present; otherwise generate
        // a memorable temp string so the admin doesn't have to type one.
        // Either way the user must change it on first sign-in (the
        // passwordChangeRequired flag still flips to true below).
        String tempPassword = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword().trim()
                : generateTempPassword();

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(tempPassword));
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
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName(), tempPassword);
                emailSent = true;
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}", user.getEmail(), e);
            }
        }

        log.info("Created user '{}' (id={}) with temp password (length={}); passwordChangeRequired=true",
                user.getUsername(), user.getId(), tempPassword.length());

        return new CreateUserResponse(toDTO(user), emailSent, tempPassword);
    }

    /** Generate an 8-char readable temp password: 3 letters + 4 digits + '!'.
     *  Not cryptographically tight; it just has to survive one sign-in
     *  before the user is forced to change it. */
    private String generateTempPassword() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O confusion
        String digits = "23456789";                  // no 0/1 confusion
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 3; i++) sb.append(letters.charAt(rnd.nextInt(letters.length())));
        for (int i = 0; i < 4; i++) sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append('!');
        return sb.toString();
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

    /**
     * Admin-set password. Also unlocks the account and resets failed-login
     * counters so a previously-locked user can sign in with the new temp
     * credential without waiting for the lock window to expire.
     *
     * The refresh-token revoke is wrapped in its own try/catch so a stuck
     * token row can't roll back the password update -- the same fix we
     * applied to user-driven changePassword.
     */
    @Transactional
    public void forcePassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(true);
        // Reset the failed-attempt + lock state so the user isn't blocked by
        // a previous testing cycle that hit MAX_FAILED_ATTEMPTS.
        user.setFailedLoginAttempts(0);
        user.setIsLocked(false);
        user.setLockExpiry(null);
        user.setIsActive(true);
        userRepository.save(user);

        log.info("Force-password applied for user '{}' (id={}); new hash prefix='{}', isLocked=false, attempts=0",
                user.getUsername(), user.getId(),
                user.getPassword().substring(0, Math.min(20, user.getPassword().length())));
    }

    /** Diagnostic snapshot of a user's auth state. Helps debug login
     *  failures by showing the actual DB values without exposing the full
     *  password hash. */
    public java.util.Map<String, Object> diagnostic(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("username", user.getUsername());
        out.put("email", user.getEmail());
        out.put("isActive", user.getIsActive());
        out.put("isLocked", user.getIsLocked());
        out.put("failedLoginAttempts", user.getFailedLoginAttempts());
        out.put("lockExpiry", user.getLockExpiry());
        out.put("passwordChangeRequired", user.getPasswordChangeRequired());
        out.put("lastLogin", user.getLastLogin());
        String hash = user.getPassword() == null ? "" : user.getPassword();
        out.put("passwordHashPrefix", hash.substring(0, Math.min(20, hash.length())));
        out.put("passwordHashLength", hash.length());
        out.put("roles", user.getRoles().stream().map(r -> r.getName()).toList());
        return out;
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
