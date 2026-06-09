package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private CollectionWorkerRepository collectionWorkerRepository;

    @Autowired
    private CollectionScheduleRepository collectionScheduleRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private OfficeStaffRepository officeStaffRepository;

    @Autowired
    private RegisteredUserPaymentRepository registeredUserPaymentRepository;

    // --- Login Request DTO Structure ---
    public static class LoginRequest {
        public String userId;
        public String password;
        public String passkey; // "adm_pass" for admin, "office_pass" for office staff
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        // 0. SYSTEM ROLE CHECK: Admin Verification Path
        if ("adm_pass".equals(request.passkey)) {
            Optional<Admin> adminOpt = adminRepository.findById(request.userId);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                if (request.password != null && request.password.equals(admin.getPassword())) {
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "page", "admin_page",
                            "adminName", admin.getAdminName() != null ? admin.getAdminName() : "Admin",
                            "passkey", "adm_pass"));
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Admin credentials.");
        }

        // 1. OFFICE STAFF WEBSITE LOGIN
        if ("office_pass".equals(request.passkey)) {
            Optional<OfficeStaff> staffOpt = officeStaffRepository.findById(request.userId);
            if (staffOpt.isPresent()) {
                OfficeStaff staff = staffOpt.get();
                if (request.password != null && request.password.equals(staff.getPassword())) {
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "page", "office_staff_page",
                            "collection_worker_name", staff.getFullName() != null ? staff.getFullName() : "N/A",
                            "passkey", "office_pass"));
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Office Staff credentials.");
        }

        // 2. FLUTTER APP FRONT END LOGIN (Resident User or Collection Worker)
        // Check Resident User first
        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(request.userId);
        if (userOpt.isPresent()) {
            RegisteredUser user = userOpt.get();
            if (request.password != null && request.password.equals(user.getPassword())) {

                // Lazy-load dynamic schedule metrics for user's assigned service zone
                String zoneId = user.getZoneId();
                String scheduledDate = "Not scheduled";
                String workerAssigned = "Not yet assigned";
                double amount = 50.0;

                if (zoneId != null) {
                    List<CollectionSchedule> schedules = collectionScheduleRepository.findByZoneId(zoneId);
                    for (CollectionSchedule s : schedules) {
                        if ("scheduled".equalsIgnoreCase(s.getStatus())) {
                            scheduledDate = s.getScheduledDate();
                            workerAssigned = s.getCollectionWorkerAssigned() != null
                                    ? s.getCollectionWorkerAssigned()
                                    : "Not yet assigned";
                            amount = s.getAmountPerHouse();
                            break;
                        }
                    }
                }

                // Dynamic fetch of actual pending payment from RegisteredUserPayment repository
                Optional<RegisteredUserPayment> rupOpt = registeredUserPaymentRepository.findById(user.getHouseId());
                double pendingPayment = rupOpt.isPresent() ? rupOpt.get().getAmountPending() : 0.0;

                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "page", "user_page",
                        "userName", user.getUserName() != null ? user.getUserName() : "N/A",
                        "pendingPayment", pendingPayment,
                        "points", user.getPoints(),
                        "wasteCollectionScheduleDate", scheduledDate,
                        "agentName", workerAssigned,
                        "amount", amount));
            }
        }

        // Check Collection Worker next
        Optional<CollectionWorker> workerOpt = collectionWorkerRepository.findById(request.userId);
        if (workerOpt.isPresent()) {
            CollectionWorker worker = workerOpt.get();
            if (request.password != null && request.password.equals(worker.getPassword())) {
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "page", "collection_worker_page",
                        "collection_worker_name", worker.getName() != null ? worker.getName() : "N/A",
                        "scheduledZoneName", worker.getScheduledZoneName() != null ? worker.getScheduledZoneName() : "N/A",
                        "numberOfHouses", worker.getNumberOfHouses() != null ? worker.getNumberOfHouses() : 0,
                        "wardNumber", worker.getWardNumber() != null ? worker.getWardNumber() : 0,
                        "villageName", worker.getVillageName() != null ? worker.getVillageName() : "N/A",
                        "passkey", ""));
            }
        }

        // Fallback early block routing: early exit with Unauthorized status message layout
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User profile not found, please sign up.");
    }
}