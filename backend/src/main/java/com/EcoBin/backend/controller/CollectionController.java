package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import com.EcoBin.backend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for collection schedule, immediate requests, OTP
 * verification,
 * zone completion, collection detail view, and worker assignment trigger.
 */
@RestController
@RequestMapping("/api/collection")
@CrossOrigin(origins = "*") // 🔥 CRITICAL FIX: Explicitly permits your Flutter client to bypass CORS
                            // security blocks
public class CollectionController {

    @Autowired
    private SchedulingService schedulingService;
    @Autowired
    private RegisteredUserRepository registeredUserRepository;
    @Autowired
    private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired
    private CollectionScheduleRepository scheduleRepository;
    @Autowired
    private RegisteredUserCollectionRepository userCollectionRepository;
    @Autowired
    private RegisteredUserPaymentRepository userPaymentRepository;

    // ===========================
    // 1. GENERATE SCHEDULE FOR A ZONE
    // ===========================

    public static class ScheduleRequest {
        public String zoneId;
    }

    @PostMapping("/schedule/generate")
    public ResponseEntity<?> generateSchedule(@RequestBody ScheduleRequest request) {
        Map<String, Object> result = schedulingService.generateScheduleForZone(request.zoneId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 2. IMMEDIATE COLLECTION REQUEST
    // ===========================

    public static class ImmediateRequest {
        public String userId;
        public String reason; // optional
    }

    @PostMapping("/immediate-request")
    public ResponseEntity<?> requestImmediateCollection(@RequestBody ImmediateRequest request) {
        Map<String, Object> result = schedulingService.handleImmediateCollectionRequest(
                request.userId, request.reason);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 3. COLLECTION DETAIL/SCHEDULE VIEW (for registered user)
    // ===========================

    @GetMapping("/schedule/view/{userId}")
    public ResponseEntity<?> viewCollectionSchedule(@PathVariable String userId) {
        System.out.println("📥 ECOBIN BACKEND: Received dashboard query for User: " + userId);
        Map<String, Object> result = new HashMap<>();

        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
        if (!userOpt.isPresent()) {
            System.out.println("❌ ECOBIN BACKEND: User ID [" + userId + "] not found in MongoDB registry.");
            result.put("status", "error");
            result.put("message", "User ID not initialized in MongoDB registry matching token.");
            return ResponseEntity.ok(result);
        }

        RegisteredUser user = userOpt.get();
        String zoneId = user.getZoneId();
        System.out.println("✓ ECOBIN BACKEND: Found User record. Assigned Zone: " + zoneId);

        // Safe Fallback initialization parameters to protect execution flows from
        // tracking locks
        String nextCollectionDate = "Pending assignment";
        String assignedWorkerName = "Not yet assigned";
        String collectionStatus = "Pending";
        double amountPending = 0.00;
        List<String> last10Dates = new ArrayList<>();

        // Secure Zone House Data Extract
        System.out.println("🛰️ ECOBIN BACKEND: Querying Zone House Details...");
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        if (houses != null) {
            for (ZoneHouseDetail h : houses) {
                if (h != null && userId.equals(h.getRegisteredUserId())) {
                    if (h.getNextCollectedDate() != null)
                        nextCollectionDate = h.getNextCollectedDate();
                    if (h.getCollectionStatus() != null)
                        collectionStatus = h.getCollectionStatus();
                    amountPending = h.getAmountPending();
                    break;
                }
            }
        }

        // Secure Worker Assignment Extract
        System.out.println("🛰️ ECOBIN BACKEND: Querying Schedule Worker Assignments...");
        List<CollectionSchedule> schedules = scheduleRepository.findByZoneId(zoneId);
        if (schedules != null) {
            for (CollectionSchedule s : schedules) {
                if (s != null && "scheduled".equals(s.getStatus()) && s.getCollectionWorkerAssigned() != null) {
                    assignedWorkerName = s.getCollectionWorkerAssigned();
                    break;
                }
            }
        }

        // Secure Historical Date Processing Check (Null-Safe Protection)
        Optional<RegisteredUserCollection> rucOpt = userCollectionRepository.findById(userId);
        if (rucOpt.isPresent() && rucOpt.get().getLast10TimeWasteCollectedDate() != null) {
            last10Dates = rucOpt.get().getLast10TimeWasteCollectedDate();
        }

        String paymentStatus = amountPending > 0 ? "pending" : "paid";

        // Dispatch clean parameters packet back onto Flutter pipeline window frames
        result.put("status", "success");
        result.put("nextCollectionDate", nextCollectionDate);
        result.put("assignedWorkerName", assignedWorkerName);
        result.put("paymentStatus", paymentStatus);
        result.put("amountPending", amountPending);
        result.put("collectionStatus", collectionStatus);
        result.put("last10CollectedDates", last10Dates);

        System.out.println("🚀 ECOBIN BACKEND: Sync dispatch payload compiled successfully for User: " + userId);
        result.put("userName", user.getUserName());
        return ResponseEntity.ok(result);

    }

    // ===========================
    // 4. OTP VERIFY & COLLECT (by collection worker)
    // ===========================

    public static class OtpVerifyRequest {
        public String houseUserId;
        public String otp;
        public boolean paymentReceivedAtDoor;
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtpAndCollect(@RequestBody OtpVerifyRequest request) {
        Map<String, Object> result = schedulingService.verifyOtpAndCollect(
                request.houseUserId, request.otp, request.paymentReceivedAtDoor);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 5. GENERATE OTP FOR ZONE (before collection day)
    // ===========================

    public static class OtpGenerateRequest {
        public String zoneId;
        public String collectionDate;
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<?> generateOtp(@RequestBody OtpGenerateRequest request) {
        schedulingService.generateOtpsForZone(request.zoneId, request.collectionDate);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "OTPs generated for zone " + request.zoneId + " on " + request.collectionDate));
    }

    // ===========================
    // 6. MARK ZONE COLLECTION COMPLETE
    // ===========================

    public static class ZoneCompleteRequest {
        public String zoneId;
        public String collectionDate;
    }

    @PostMapping("/zone-complete")
    public ResponseEntity<?> markZoneComplete(@RequestBody ZoneCompleteRequest request) {
        schedulingService.markZoneCollectionComplete(request.zoneId, request.collectionDate);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Zone " + request.zoneId + " collection marked complete for " + request.collectionDate));
    }

}