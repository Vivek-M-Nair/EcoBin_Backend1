package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.Notification;
import com.EcoBin.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing resident notifications.
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<?> getNotifications(@PathVariable String userId) {
        List<Notification> list = notificationRepository.findByUserId(userId);
        // Sort by createdAt descending
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable String id) {
        if (notificationRepository.existsById(id)) {
            notificationRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Notification deleted successfully"));
        } else {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Notification not found"));
        }
    }
}
