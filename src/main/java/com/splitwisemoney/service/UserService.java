package com.splitwisemoney.service;

import com.splitwisemoney.entity.User;
import com.splitwisemoney.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final GroupService groupService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ActivityLogService activityLogService,
                       @Lazy GroupService groupService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.groupService = groupService;
    }

    @Transactional
    public User registerUser(String fullName, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered!");
        }

        User user = new User(fullName, email, passwordEncoder.encode(password));
        User savedUser = userRepository.save(user);

        activityLogService.log(savedUser, "User account registered.");

        // Auto-accept any pending invitations sent to this email
        groupService.autoAcceptPendingInvitations(savedUser);

        return savedUser;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User updateProfile(User user, String fullName) {
        user.setFullName(fullName);
        User updated = userRepository.save(user);
        activityLogService.log(updated, "Profile details updated.");
        return updated;
    }

    @Transactional
    public void changePassword(User user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password!");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        activityLogService.log(user, "Password changed successfully.");
    }
}
