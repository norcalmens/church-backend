package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.*;
import com.norcalretreat.backend.entity.PasswordResetToken;
import com.norcalretreat.backend.entity.RefreshToken;
import com.norcalretreat.backend.entity.Role;
import com.norcalretreat.backend.entity.User;
import com.norcalretreat.backend.repository.PasswordResetTokenRepository;
import com.norcalretreat.backend.repository.RefreshTokenRepository;
import com.norcalretreat.backend.repository.RoleRepository;
import com.norcalretreat.backend.repository.UserRepository;
import com.norcalretreat.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private EmailService emailService;

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    // Lockout disabled per ops request -- effectively unreachable. Restore
    // by lowering MAX_FAILED_ATTEMPTS when we want brute-force protection
    // back. Was: 5 attempts -> 30 min lock.
    private static final int MAX_FAILED_ATTEMPTS = Integer.MAX_VALUE;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final int MAX_RESET_REQUESTS_PER_WINDOW = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 15;

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("LOGIN attempt: identifier='{}' from ip={}", request.getUsername(), ipAddress);

        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElse(null);
        if (user == null) {
            log.info("LOGIN failed: no user matched username-or-email='{}'", request.getUsername());
            throw new IllegalArgumentException("Invalid username or password");
        }
        log.info("LOGIN found user id={}, username='{}', isActive={}, isLocked={}, attempts={}, hashPrefix='{}'",
                user.getId(), user.getUsername(),
                user.getIsActive(), user.getIsLocked(), user.getFailedLoginAttempts(),
                user.getPassword() == null ? "" : user.getPassword().substring(0,
                        Math.min(20, user.getPassword().length())));

        // Auto-clear any pre-existing lock state on every login attempt so an
        // old lock can never block a current sign-in. The lockout feature is
        // disabled (MAX_FAILED_ATTEMPTS = Integer.MAX_VALUE) but this keeps
        // legacy rows from causing surprise rejections.
        if (Boolean.TRUE.equals(user.getIsLocked())
                || (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockExpiry() != null) {
            log.info("LOGIN clearing stale lock state for user '{}': wasLocked={}, attempts={}, lockExpiry={}",
                    user.getUsername(), user.getIsLocked(), user.getFailedLoginAttempts(), user.getLockExpiry());
            user.setIsLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockExpiry(null);
            userRepository.save(user);
        }

        // Check if account is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.info("LOGIN failed: account inactive for user '{}'", user.getUsername());
            auditService.logEvent("LOGIN_FAILED", request.getUsername(), user.getId(),
                    ipAddress, userAgent, "Account is inactive", false);
            throw new IllegalArgumentException("Account is inactive.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            log.info("LOGIN failed: authenticate() threw {} for user '{}' -- message='{}'",
                    e.getClass().getSimpleName(), user.getUsername(), e.getMessage());
            // Don't increment / lock -- counter stays at 0 because the
            // lockout is disabled. The audit row still records the failure.
            auditService.logEvent("LOGIN_FAILED", request.getUsername(), user.getId(),
                    ipAddress, userAgent, "Invalid credentials", false);
            throw new IllegalArgumentException("Invalid username or password");
        }
        log.info("LOGIN success for user '{}'", user.getUsername());

        // Success: reset failed attempts, update last login
        user.setFailedLoginAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        RefreshToken refreshToken = createRefreshToken(user, ipAddress, userAgent);

        auditService.logEvent("LOGIN", user.getUsername(), user.getId(),
                ipAddress, userAgent, "Login successful", true);

        return buildAuthResponse(user, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        Role memberRole = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new RuntimeException("Default role MEMBER not found"));
        user.getRoles().add(memberRole);

        user = userRepository.save(user);

        RefreshToken refreshToken = createRefreshToken(user, null, null);

        auditService.logEvent("REGISTER", user.getUsername(), user.getId(),
                null, null, "User registered", true);

        return buildAuthResponse(user, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (Boolean.TRUE.equals(storedToken.getIsRevoked())) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        if (storedToken.isExpired()) {
            storedToken.setIsRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new IllegalArgumentException("Refresh token has expired");
        }

        storedToken.setIsRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        RefreshToken newRefreshToken = createRefreshToken(user, storedToken.getIpAddress(), storedToken.getDeviceInfo());

        return buildAuthResponse(user, newRefreshToken.getToken());
    }

    @Transactional
    public void logout(String username, String refreshToken) {
        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
                token.setIsRevoked(true);
                refreshTokenRepository.save(token);
            });
        } else {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            refreshTokenRepository.revokeAllByUser(user);
        }

        auditService.logEvent("LOGOUT", username, null,
                null, null, "User logged out", true);
    }

    /**
     * Persist the new password hash. Runs in its OWN transaction so that
     * downstream side-effects (refresh-token revoke, audit log) can't roll
     * the change back via UnexpectedRollbackException. Previously, a
     * failure inside the audit-log save would mark the wrapping
     * transaction rollback-only -- the password update appeared to succeed
     * (HTTP 200) but never actually committed, leaving the user with their
     * old credentials and no error message.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);

        log.info("Password updated for user '{}' (id={}); new hash prefix='{}'",
                username, user.getId(),
                user.getPassword().substring(0, Math.min(20, user.getPassword().length())));
    }

    /**
     * Best-effort side effects to run AFTER changePassword has committed.
     * The controller calls this in its own try/catch; a failure here logs
     * but cannot undo the password update. Done as a separate public
     * @Transactional method so each call gets a fresh transaction --
     * self-invocation inside the same bean would have bypassed the
     * proxy and re-joined the previous transaction.
     */
    @Transactional
    public void recordPasswordChangeSideEffects(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        refreshTokenRepository.revokeAllByUser(user);
        auditService.logEvent("PASSWORD_CHANGE", username, user.getId(),
                null, null, "Password changed", true);
    }

    public UserDTO getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return convertToUserDTO(user);
    }

    @Transactional
    public void forgotPassword(String email, String ipAddress, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            auditService.logEvent("PASSWORD_RESET_REQUEST", email, null,
                    ipAddress, userAgent, "Email not found (no action taken)", true);
            return;
        }

        User user = userOpt.get();

        long recentRequests = passwordResetTokenRepository
                .countByUserAndCreatedAtAfterAndIsUsedFalse(user, LocalDateTime.now().minusMinutes(RATE_LIMIT_WINDOW_MINUTES));

        if (recentRequests >= MAX_RESET_REQUESTS_PER_WINDOW) {
            log.warn("Rate limit exceeded for password reset: user={}", user.getUsername());
            auditService.logEvent("PASSWORD_RESET_REQUEST", user.getUsername(), user.getId(),
                    ipAddress, userAgent, "Rate limit exceeded", false);
            return;
        }

        passwordResetTokenRepository.invalidateAllByUser(user);

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(hashedToken);
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        resetToken.setIpAddress(ipAddress);
        passwordResetTokenRepository.save(resetToken);

        if (emailService != null) {
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), rawToken, user.getFirstName());
            } catch (Exception e) {
                log.error("Failed to send password reset email for user: {}", user.getUsername(), e);
            }
        } else {
            log.warn("EmailService not available — password reset token generated but email not sent. Token for dev: {}", rawToken);
        }

        auditService.logEvent("PASSWORD_RESET_REQUEST", user.getUsername(), user.getId(),
                ipAddress, userAgent, "Password reset email sent", true);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress, String userAgent) {
        String hashedToken = hashToken(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (Boolean.TRUE.equals(resetToken.getIsUsed())) {
            throw new IllegalArgumentException("This reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("This reset token has expired");
        }

        resetToken.setIsUsed(true);
        passwordResetTokenRepository.save(resetToken);

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        if (Boolean.TRUE.equals(user.getIsLocked())) {
            user.setIsLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockExpiry(null);
        }

        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);

        auditService.logEvent("PASSWORD_RESET", user.getUsername(), user.getId(),
                ipAddress, userAgent, "Password reset successful", true);
    }

    @Transactional
    public AuthResponse completeRegistration(CompleteRegistrationRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.getEmail(), request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("No account found with that email or username"));

        if (!Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
            throw new IllegalArgumentException("This account has already been activated");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangeRequired(false);
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        userRepository.save(user);

        RefreshToken refreshToken = createRefreshToken(user, null, null);

        auditService.logEvent("REGISTRATION_COMPLETE", user.getUsername(), user.getId(),
                null, null, "User completed registration", true);

        if (emailService != null) {
            emailService.sendAccountActivatedEmail(user.getEmail(), user.getFirstName());
        } else {
            log.warn("EmailService not available — activation email not sent for user: {}", user.getEmail());
        }

        return buildAuthResponse(user, refreshToken.getToken());
    }

    // ---- Private helpers ----

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private AuthResponse buildAuthResponse(User user, String refreshTokenValue) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .distinct()
                .collect(Collectors.toList());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUsername(), user.getId(), roles, permissions);

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roles)
                .permissions(permissions)
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .passwordChangeRequired(Boolean.TRUE.equals(user.getPasswordChangeRequired()))
                .build();
    }

    private RefreshToken createRefreshToken(User user, String ipAddress, String deviceInfo) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(
                jwtTokenProvider.getRefreshTokenExpiration() / 1000));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setDeviceInfo(deviceInfo);
        return refreshTokenRepository.save(refreshToken);
    }

    private UserDTO convertToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRoles(user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList()));
        dto.setIsActive(user.getIsActive());
        dto.setIsLocked(user.getIsLocked());
        dto.setLastLogin(user.getLastLogin());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setPasswordChangeRequired(user.getPasswordChangeRequired());
        return dto;
    }
}
