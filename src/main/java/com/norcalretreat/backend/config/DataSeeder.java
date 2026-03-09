package com.norcalretreat.backend.config;

import com.norcalretreat.backend.entity.Permission;
import com.norcalretreat.backend.entity.Role;
import com.norcalretreat.backend.entity.User;
import com.norcalretreat.backend.repository.RoleRepository;
import com.norcalretreat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public CommandLineRunner seedRolesAndPermissions() {
        return args -> {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> seedPermissionsAndRoles());
            tx.executeWithoutResult(status -> seedDefaultAdmin());
        };
    }

    private void seedPermissionsAndRoles() {
        if (roleRepository.existsByName("ADMIN")) {
            log.info("Roles already exist, skipping seed");
            return;
        }

        log.info("Seeding default roles and permissions...");

        // Create permissions
        Permission viewRegistrations = createPermission("VIEW_REGISTRATIONS", "View all registrations", "REGISTRATION");
        Permission manageRegistrations = createPermission("MANAGE_REGISTRATIONS", "Create/edit/delete registrations", "REGISTRATION");
        Permission viewPayments = createPermission("VIEW_PAYMENTS", "View payment records", "PAYMENT");
        Permission manageUsers = createPermission("MANAGE_USERS", "Create/edit/delete users", "USER");
        Permission viewStats = createPermission("VIEW_STATS", "View retreat statistics", "ADMIN");

        // Create ADMIN role
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Administrator with full access");
        Set<Permission> adminPermissions = new HashSet<>();
        adminPermissions.add(viewRegistrations);
        adminPermissions.add(manageRegistrations);
        adminPermissions.add(viewPayments);
        adminPermissions.add(manageUsers);
        adminPermissions.add(viewStats);
        adminRole.setPermissions(adminPermissions);
        roleRepository.save(adminRole);

        // Create MEMBER role
        Role memberRole = new Role();
        memberRole.setName("MEMBER");
        memberRole.setDescription("Regular retreat attendee");
        Set<Permission> memberPermissions = new HashSet<>();
        memberPermissions.add(viewRegistrations);
        memberRole.setPermissions(memberPermissions);
        roleRepository.save(memberRole);

        // Create SUPERADMIN role
        Role superadminRole = new Role();
        superadminRole.setName("SUPERADMIN");
        superadminRole.setDescription("Super administrator with unrestricted access");
        superadminRole.setPermissions(new HashSet<>(adminPermissions));
        roleRepository.save(superadminRole);

        // Create SUPERUSER role
        Role superuserRole = new Role();
        superuserRole.setName("SUPERUSER");
        superuserRole.setDescription("Elevated user with extended privileges");
        Set<Permission> superuserPermissions = new HashSet<>();
        superuserPermissions.add(viewRegistrations);
        superuserPermissions.add(viewPayments);
        superuserPermissions.add(viewStats);
        superuserRole.setPermissions(superuserPermissions);
        roleRepository.save(superuserRole);

        log.info("Seeded roles: ADMIN, MEMBER, SUPERADMIN, SUPERUSER with {} permissions", adminPermissions.size());

    }

    private void seedDefaultAdmin() {
        if (userRepository.existsByUsername("admin")) {
            log.info("Default admin user already exists, skipping");
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN role not found — roles must be seeded first"));

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@norcalmensretreat.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setIsActive(true);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        log.info("Seeded default admin user (username: admin, password: admin123)");
    }

    private Permission createPermission(String name, String description, String category) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setDescription(description);
        permission.setCategory(category);
        entityManager.persist(permission);
        return permission;
    }
}
