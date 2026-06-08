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
            // Fixed: Changed from .findById(request.userId) to look at your model's ID
            // field mapping if required,
            // but findById checks the @Id annotated field, which is your userId.
            Optional<Admin> adminOpt = adminRepository.findById(request.userId);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                if (request.password != null && request.password.equals(admin.getPassword())) {
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "page", "admin_page",
                            "adminName", admin.getAdminName() != null ? admin.getAdminName() : "Admin", // Fixed:
                                                                                                        // .getName() ->
                                                                                                        // .getAdminName()
                            "passkey", "adm_pass"));
                }
            }
            // Strict security lock: If passkey is admin but credentials fail, block routing
            // early
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Admin credentials.");
        }

        // 1. DYNAMIC SYSTEM RUNTIME ROUTE: Registered Resident Profile Evaluation
        // Note: Change to findByEmailId or findByUserName if users do not log in via
        // their "house-xxxx" primary key identifier.
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

                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "page", "user_page",
                        "userName", user.getUserName() != null ? user.getUserName() : "N/A",
                        "pendingPayment", user.getPendingPayment(),
                        "points", user.getPoints(),
                        "wasteCollectionScheduleDate", scheduledDate,
                        "agentName", workerAssigned,
                        "amount", amount));
            }
        }

        // 2. BACKEND COLLECTION TRACKING ROUTE: Field Worker & Office Staff
        // Verification
        Optional<CollectionWorker> workerOpt = collectionWorkerRepository.findById(request.userId);
        if (workerOpt.isPresent()) {
            CollectionWorker worker = workerOpt.get();
            if (request.password != null && request.password.equals(worker.getPassword())) {

                boolean isOfficeStaff = "OFFICE_STAFF".equalsIgnoreCase(worker.getRole());

                // Enforce role passkey protection verification for office personnel
                if (isOfficeStaff && !"office_pass".equals(request.passkey)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("Missing or invalid Office Passkey role verification identifier.");
                }

                String page = isOfficeStaff ? "office_staff_page" : "collection_worker_page";
                String passkey = isOfficeStaff ? "office_pass" : "";

                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "page", page,
                        "collection_worker_name", worker.getName() != null ? worker.getName() : "N/A",
                        "scheduledZoneName",
                        worker.getScheduledZoneName() != null ? worker.getScheduledZoneName() : "N/A",
                        "numberOfHouses", worker.getNumberOfHouses() != null ? worker.getNumberOfHouses() : 0,
                        "wardNumber", worker.getWardNumber() != null ? worker.getWardNumber() : 0,
                        "villageName", worker.getVillageName() != null ? worker.getVillageName() : "N/A",
                        "passkey", passkey));
            }
        }

        // 3. SECURE THREAD PROTECTION FALLBACK: Return standard HTTP 401 Explicit
        // Unauthorized status response layout mapping context
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User profile not found, please sign up.");
    }
}