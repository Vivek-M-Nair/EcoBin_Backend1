package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import com.EcoBin.backend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    @Autowired
    private CollectionWorkerRepository collectionWorkerRepository;
    @Autowired
    private UserRequestedDataRepository userRequestedDataRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private CollectionWorkerScheduleRepository collectionWorkerScheduleRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private LastTimeCollectionRepository lastTimeCollectionRepository;
    @Autowired
    private StateDistRepository stateDistRepository;

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
        if ("error".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
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
                if (s != null && "scheduled".equals(s.getStatus()) && s.getCollectionWorkerAssigned() != null && !s.getCollectionWorkerAssigned().isEmpty()) {
                    String workerId = s.getCollectionWorkerAssigned();
                    Optional<CollectionWorker> workerOpt = collectionWorkerRepository.findById(workerId);
                    if (workerOpt.isPresent()) {
                        assignedWorkerName = workerOpt.get().getName();
                    } else {
                        assignedWorkerName = workerId;
                    }
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

        // Retrieve zone's last collection date
        String zoneLastCollectedDate = "Never";
        Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
        if (ltcOpt.isPresent() && ltcOpt.get().getLastCollectedDate() != null) {
            zoneLastCollectedDate = ltcOpt.get().getLastCollectedDate();
        }

        // Dispatch clean parameters packet back onto Flutter pipeline window frames
        result.put("status", "success");
        result.put("nextCollectionDate", nextCollectionDate);
        result.put("assignedWorkerName", assignedWorkerName);
        result.put("paymentStatus", paymentStatus);
        result.put("amountPending", amountPending);
        result.put("collectionStatus", collectionStatus);
        result.put("last10CollectedDates", last10Dates);
        result.put("zoneLastCollectedDate", zoneLastCollectedDate);

        System.out.println("🚀 ECOBIN BACKEND: Sync dispatch payload compiled successfully for User: " + userId + ", Zone last collection: " + zoneLastCollectedDate);
        result.put("userName", user.getUserName());
        result.put("points", user.getPoints());
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

    // ===========================
    // 7. GET ALL IMMEDIATE REQUESTS
    // ===========================

    @GetMapping("/immediate-requests")
    public ResponseEntity<?> getImmediateRequests() {
        List<UserRequestedData> requests = userRequestedDataRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        for (UserRequestedData req : requests) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", req.getId());
            map.put("userId", req.getUserId());
            map.put("zoneId", req.getZoneId());
            map.put("paymentAmount", req.getPaymentAmount());
            map.put("paymentStatus", req.getPaymentStatus());
            map.put("requestedDate", req.getRequestedDate());
            map.put("lastCollectedDateInZone", req.getLastCollectedDateInZone());
            map.put("nextScheduledDate", req.getNextScheduledDate());
            map.put("validateOtp", req.getValidateOtp());
            map.put("otpGen", req.getOtpGen());
            map.put("status", req.getStatus());
            map.put("workerId", req.getWorkerId());

            Optional<RegisteredUser> userOpt = registeredUserRepository.findById(req.getUserId());
            if (userOpt.isPresent()) {
                RegisteredUser u = userOpt.get();
                map.put("userName", u.getUserName());
                map.put("houseName", u.getHouseName());
                map.put("houseNumber", u.getHouseNumber());
                map.put("panchayathName", u.getPanchayathOrMunicipalityName());
                map.put("wardNo", u.getWardNo());
                map.put("phoneNumber", u.getPhoneNumber());
            }
            response.add(map);
        }
        return ResponseEntity.ok(response);
    }

    // ===========================
    // 8. GET AVAILABLE WORKERS FOR DATE
    // ===========================

    @GetMapping("/available-workers")
    public ResponseEntity<?> getAvailableWorkers(@RequestParam String date) {
        List<CollectionWorker> workers = collectionWorkerRepository.findByRole("WORKER");
        List<CollectionWorker> available = new ArrayList<>();
        for (CollectionWorker worker : workers) {
            String workerId = worker.getCollectionWorkerId();

            // Check assignments
            List<CollectionSchedule> assignments = scheduleRepository.findByCollectionWorkerAssignedAndScheduledDate(workerId, date);
            if (!assignments.isEmpty()) {
                continue;
            }

            // Check leaves
            List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
            boolean onLeave = leaves.stream()
                    .anyMatch(l -> "approved".equalsIgnoreCase(l.getStatus())
                            && date.equals(l.getLeaveRequestedDate()));
            if (onLeave) {
                continue;
            }

            // Check immediate requests (worker busy if accepted or scheduled for this date)
            List<UserRequestedData> reqAssignments = userRequestedDataRepository.findByWorkerIdAndNextScheduledDate(workerId, date);
            if (reqAssignments != null && !reqAssignments.isEmpty()) {
                boolean hasAcceptedReq = reqAssignments.stream().anyMatch(r -> 
                    "accepted".equalsIgnoreCase(r.getStatus()) || "scheduled".equalsIgnoreCase(r.getStatus())
                );
                if (hasAcceptedReq) {
                    continue;
                }
            }

            available.add(worker);
        }
        return ResponseEntity.ok(available);
    }

    // ===========================
    // 9. ACCEPT IMMEDIATE REQUEST
    // ===========================

    public static class RequestAcceptPayload {
        public String requestId;
        public String date;     // format: dd-MM-yyyy
        public String workerId;
    }

    @PostMapping("/immediate-request/accept")
    public ResponseEntity<?> acceptImmediateRequest(@RequestBody RequestAcceptPayload payload) {
        Optional<UserRequestedData> reqOpt = userRequestedDataRepository.findById(payload.requestId);
        if (!reqOpt.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Request not found"));
        }
        UserRequestedData request = reqOpt.get();
        String userId = request.getUserId();
        String zoneId = request.getZoneId();

        // Enforce the scheduled collection date as tomorrow (next day of accepting date)
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowStr = tomorrow.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        // Determine which worker to assign
        String assignedWorkerId = null;
        List<CollectionWorker> workers = collectionWorkerRepository.findByRole("WORKER");

        // 1. Try to assign the requested worker if they are available
        if (payload.workerId != null && !payload.workerId.trim().isEmpty()) {
            if (isWorkerAvailable(payload.workerId.trim(), tomorrowStr)) {
                assignedWorkerId = payload.workerId.trim();
            }
        }

        // 2. Otherwise, auto-assign the first available worker who has not been allotted a collection date for tomorrow
        if (assignedWorkerId == null) {
            for (CollectionWorker worker : workers) {
                String wId = worker.getCollectionWorkerId();
                if (isWorkerAvailable(wId, tomorrowStr)) {
                    assignedWorkerId = wId;
                    break;
                }
            }
        }

        // 3. Fallback: If every collection worker has been scheduled, randomly assign a worker in the same district as the requested user
        if (assignedWorkerId == null) {
            Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
            if (userOpt.isPresent()) {
                RegisteredUser user = userOpt.get();
                String userDistId = user.getDistId();
                String userDistName = null;
                if (userDistId != null) {
                    Optional<StateDist> sdOpt = stateDistRepository.findById(userDistId);
                    userDistName = sdOpt.isPresent() ? sdOpt.get().getDistName() : userDistId;
                }
                
                final String targetDist = userDistName;
                List<CollectionWorker> districtWorkers = new ArrayList<>();
                if (targetDist != null) {
                    for (CollectionWorker worker : workers) {
                        if (worker.getDistrict() != null && targetDist.trim().equalsIgnoreCase(worker.getDistrict().trim())) {
                            districtWorkers.add(worker);
                        }
                    }
                }

                if (!districtWorkers.isEmpty()) {
                    CollectionWorker randomWorker = districtWorkers.get(new Random().nextInt(districtWorkers.size()));
                    assignedWorkerId = randomWorker.getCollectionWorkerId();
                }
            }
        }

        // 4. Absolute fallback: if no worker in district, choose any worker randomly
        if (assignedWorkerId == null && !workers.isEmpty()) {
            CollectionWorker randomWorker = workers.get(new Random().nextInt(workers.size()));
            assignedWorkerId = randomWorker.getCollectionWorkerId();
        }

        if (assignedWorkerId == null) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "No workers available in the system repository."));
        }

        Optional<CollectionWorker> workerOpt = collectionWorkerRepository.findById(assignedWorkerId);
        String workerName = workerOpt.isPresent() ? workerOpt.get().getName() : assignedWorkerId;

        // Create a 4-digit OTP
        String otp = String.format("%04d", new Random().nextInt(10000));

        // Update Request status, date, otp, workerId
        request.setStatus("scheduled");
        request.setNextScheduledDate(tomorrowStr);
        request.setWorkerId(assignedWorkerId);
        request.setOtpGen(otp);
        userRequestedDataRepository.save(request);

        // Update ZoneHouseDetail for this house only
        Optional<ZoneHouseDetail> houseOpt = zoneHouseDetailRepository.findById(userId);
        if (houseOpt.isPresent()) {
            ZoneHouseDetail house = houseOpt.get();
            house.setNextCollectedDate(tomorrowStr);
            house.setCollectionStatus("immediate_pending");
            house.setOtp(otp);
            zoneHouseDetailRepository.save(house);
        }

        // Update RegisteredUserCollection for this user only
        Optional<RegisteredUserCollection> rucOpt = userCollectionRepository.findById(userId);
        if (rucOpt.isPresent()) {
            RegisteredUserCollection ruc = rucOpt.get();
            ruc.setNextScheduledDate(tomorrowStr);
            ruc.setLastCollectionStatus("immediate_pending");
            userCollectionRepository.save(ruc);
        }

        // Create notification for the resident user
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle("Pickup Request Scheduled");
        notification.setMessage("Your immediate waste collection request has been scheduled. Scheduled date: " + tomorrowStr + ". Assigned Worker: " + workerName + ". OTP for collection is: " + otp + ". Please share this OTP with the worker.");
        notificationRepository.save(notification);

        // Update worker schedule
        updateWorkerScheduleForDate(assignedWorkerId, workerName, zoneId, tomorrowStr);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Request accepted successfully for tomorrow (" + tomorrowStr + ")"));
    }

    private boolean isWorkerAvailable(String workerId, String date) {
        // Check assignments
        List<CollectionSchedule> assignments = scheduleRepository.findByCollectionWorkerAssignedAndScheduledDate(workerId, date);
        if (!assignments.isEmpty()) {
            return false;
        }

        // Check leaves
        List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
        boolean onLeave = leaves.stream()
                .anyMatch(l -> "approved".equalsIgnoreCase(l.getStatus())
                        && date.equals(l.getLeaveRequestedDate()));
        if (onLeave) {
            return false;
        }

        // Check immediate requests (worker busy if accepted or scheduled for this date)
        List<UserRequestedData> reqAssignments = userRequestedDataRepository.findByWorkerIdAndNextScheduledDate(workerId, date);
        if (reqAssignments != null && !reqAssignments.isEmpty()) {
            boolean hasAcceptedReq = reqAssignments.stream().anyMatch(r -> 
                "accepted".equalsIgnoreCase(r.getStatus()) || "scheduled".equalsIgnoreCase(r.getStatus())
            );
            if (hasAcceptedReq) {
                return false;
            }
        }
        return true;
    }

    private void updateWorkerScheduleForDate(String workerId, String workerName, String zoneId, String date) {
        CollectionWorkerSchedule cws = collectionWorkerScheduleRepository.findById(workerId)
                .orElse(new CollectionWorkerSchedule());
        cws.setCollectionWorkerId(workerId);
        cws.setName(workerName);
        cws.setAssignedZoneId(zoneId);
        cws.setNumberOfAssignedDates(cws.getNumberOfAssignedDates() + 1);
        if (cws.getAssignedDates() == null) {
            cws.setAssignedDates(new ArrayList<>());
        }
        if (!cws.getAssignedDates().contains(date)) {
            cws.getAssignedDates().add(date);
        }
        collectionWorkerScheduleRepository.save(cws);
    }

    // ===========================
    // 10. DECLINE IMMEDIATE REQUEST
    // ===========================

    public static class RequestDeclinePayload {
        public String requestId;
        public String reason;
    }

    @PostMapping("/immediate-request/decline")
    public ResponseEntity<?> declineImmediateRequest(@RequestBody RequestDeclinePayload payload) {
        Optional<UserRequestedData> reqOpt = userRequestedDataRepository.findById(payload.requestId);
        if (!reqOpt.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Request not found"));
        }
        UserRequestedData request = reqOpt.get();
        request.setStatus("declined");
        userRequestedDataRepository.save(request);

        // Create notification for user
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setTitle("Pickup Request Declined");
        notification.setMessage("Your immediate waste collection request has been declined. Reason: " + payload.reason);
        notificationRepository.save(notification);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Request declined successfully"));
    }

    @PostMapping("/assign-all-otps")
    public ResponseEntity<?> assignAllOtps() {
        Map<String, Object> result = schedulingService.assignOtpsToAllScheduledZones();
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 11. DEDICATED IMMEDIATE REQUEST OTP VERIFY
    // ===========================

    @PostMapping("/immediate-request/verify-otp")
    public ResponseEntity<?> verifyImmediateRequestOtp(@RequestBody OtpVerifyRequest request) {
        Map<String, Object> result = schedulingService.verifyOtpAndCollect(
                request.houseUserId, request.otp, request.paymentReceivedAtDoor);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 12. SKIP HOUSE (reschedule paid house for tomorrow)
    // ===========================

    public static class SkipHouseRequest {
        public String houseUserId;
        public String reason;
    }

    @PostMapping("/skip-house")
    public ResponseEntity<?> skipHouse(@RequestBody SkipHouseRequest request) {
        Map<String, Object> result = schedulingService.skipHouse(request.houseUserId, request.reason);
        return ResponseEntity.ok(result);
    }

}