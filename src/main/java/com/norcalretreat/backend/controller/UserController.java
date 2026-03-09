package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.*;
import com.norcalretreat.backend.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        List<UserDTO> users = userManagementService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(@PathVariable Long id) {
        try {
            UserDTO user = userManagementService.getUser(id);
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateUserResponse>> createUser(@RequestBody AdminCreateUserRequest request) {
        try {
            CreateUserResponse response = userManagementService.createUser(request);
            return ResponseEntity.ok(ApiResponse.success("User created successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Long id, @RequestBody UserDTO dto) {
        try {
            UserDTO updated = userManagementService.updateUser(id, dto);
            return ResponseEntity.ok(ApiResponse.success("User updated", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long id) {
        try {
            userManagementService.deactivateUser(id);
            return ResponseEntity.ok(ApiResponse.success("User deactivated", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{userId}/roles/{roleName}")
    public ResponseEntity<ApiResponse<UserDTO>> assignRole(@PathVariable Long userId, @PathVariable String roleName) {
        try {
            UserDTO updated = userManagementService.assignRole(userId, roleName);
            return ResponseEntity.ok(ApiResponse.success("Role assigned", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    public ResponseEntity<ApiResponse<UserDTO>> removeRole(@PathVariable Long userId, @PathVariable String roleName) {
        try {
            UserDTO updated = userManagementService.removeRole(userId, roleName);
            return ResponseEntity.ok(ApiResponse.success("Role removed", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<UserDTO>> unlockUser(@PathVariable Long id) {
        try {
            UserDTO updated = userManagementService.unlockUser(id);
            return ResponseEntity.ok(ApiResponse.success("User unlocked", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}/force-password")
    public ResponseEntity<ApiResponse<Void>> forcePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String password = body.get("password");
            userManagementService.forcePassword(id, password);
            return ResponseEntity.ok(ApiResponse.success("Password updated", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable Long id) {
        try {
            userManagementService.forceLogout(id);
            return ResponseEntity.ok(ApiResponse.success("User logged out", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableRoles() {
        List<String> roles = userManagementService.getAvailableRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }
}
