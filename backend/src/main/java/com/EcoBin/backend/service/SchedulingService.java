package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core scheduling service implementing zone-based waste collection scheduling.
 *
 * Rules:
 * 1. In the same panchayath, only 2 concurrent wards share the same scheduled
 * date.
 * 2. After collection, next date is scheduled only after 15 days.
 * 3. After 15 days, backend assigns a new date 24-28 days from last collected
 * date.
 * 4. Normal amount = ₹50 per house.
 * 5. Immediate request (after 15 days) reschedules zone to 17-21 days,
 * requester pays ₹60, others ₹50.
 * 6. Immediate request (before 15 days) assigns dedicated worker, requester
 * pays ₹100, date 1-2 days out.
 */
@Service
public class SchedulingService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final double NORMAL_AMOUNT = 50.0;
    private static final double IMMEDIATE_AFTER_15_AMOUNT = 60.0;
    private static final double IMMEDIATE_BEFORE_15_AMOUNT = 100.0;

    @Autowired
    private LastTimeCollectionRepository lastTimeCollectionRepository;
    @Autowired
    private CollectionScheduleRepository collectionScheduleRepository;
    @Autowired
    private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired
    private RegisteredUserCollectionRepository registeredUserCollectionRepository;
    @Autowired
    private RegisteredUserPaymentRepository registeredUserPaymentRepository;
    @Autowired
    private RegisteredUserRepository registeredUserRepository;
    @Autowired
    private ImmediateCollectionReasonRepository immediateCollectionReasonRepository;
    @Autowired
    private CollectionWorkerRepository collectionWorkerRepository;
    @Autowired
    private CollectionWorkerScheduleRepository collectionWorkerScheduleRepository;
    @Autowired
    private CollectionScheduleRepository scheduleRepository;
    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private ZoneKottayamRepository zoneKottayamRepository;
    @Autowired
    private ZoneErnakulamRepository zoneErnakulamRepository;
    @Autowired
    private UserRequestedDataRepository userRequestedDataRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    // ===========================
    // 1. GENERATE SCHEDULE FOR ZONE (called by scheduler or manually)
    // ===========================

    /**
     * After 15 days from last collection, assign new date between 24-28 days from
     * last collected date.
     */
    public Map<String, Object> generateScheduleForZone(String zoneId) {
        ZoningService.zoningLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();

            Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
            if (!ltcOpt.isPresent()) {
                result.put("status", "error");
                result.put("message", "No collection history found for zone: " + zoneId);
                return result;
            }

            LastTimeCollection ltc = ltcOpt.get();
            LocalDate lastCollected = parseLastCollectedDate(ltc.getLastCollectedDate());
            LocalDate today = LocalDate.now();
            long daysSinceCollection = ChronoUnit.DAYS.between(lastCollected, today);

            if (daysSinceCollection < 15) {
                result.put("status", "too_early");
                result.put("message", "Only " + daysSinceCollection + " days since last collection. Wait until 15 days.");
                return result;
            }

            // Assign new date between 24-28 days from last collected date
            int randomDays = 24 + new Random().nextInt(5); // 24 to 28
            LocalDate newScheduledDate = lastCollected.plusDays(randomDays);

            // If the computed date is in the past, push it forward
            if (newScheduledDate.isBefore(today)) {
                newScheduledDate = today.plusDays(2);
            }

            String scheduledDateStr = newScheduledDate.format(DATE_FMT);

            // Create schedule entry
            String scheduleId = "SCH-" + zoneId + "-" + UUID.randomUUID().toString().substring(0, 6);
            CollectionSchedule schedule = new CollectionSchedule();
            schedule.setScheduleId(scheduleId);
            schedule.setZoneId(zoneId);
            schedule.setScheduledDate(scheduledDateStr);
            schedule.setAmountPerHouse(NORMAL_AMOUNT);
            schedule.setStatus("scheduled");
            collectionScheduleRepository.save(schedule);

            // Update all houses in this zone
            updateZoneHousesForSchedule(zoneId, scheduledDateStr, NORMAL_AMOUNT);

            result.put("status", "scheduled");
            result.put("scheduleId", scheduleId);
            result.put("scheduledDate", scheduledDateStr);
            result.put("amountPerHouse", NORMAL_AMOUNT);
            return result;
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    // ===========================
    // 2. IMMEDIATE COLLECTION REQUEST
    // ===========================

    /**
     * Handle immediate collection request from a registered user.
     * - If > 15 days since last collection OR date already scheduled: reschedule
     * zone to 17-21 days.
     * Requester pays ₹60, others pay ₹50.
     * - If < 15 days since last collection: assign dedicated worker, requester pays
     * ₹100.
     * Next date for requester = 1-2 days from now. After collection, syncs back to
     * zone schedule.
     */
    public Map<String, Object> handleImmediateCollectionRequest(String userId, String reason) {
        ZoningService.zoningLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();

            // Find the user and their zone
            Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
            if (!userOpt.isPresent()) {
                result.put("status", "error");
                result.put("message", "User not found");
                return result;
            }
            RegisteredUser user = userOpt.get();
            String zoneId = user.getZoneId();

            if (zoneId == null || zoneId.isEmpty()) {
                result.put("status", "error");
                result.put("message", "User is not assigned to any zone");
                return result;
            }

            // Check last collection date for this zone
            Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
            LocalDate lastCollectedDate = null;
            long daysSinceLastCollection = 999; // large default

            if (!ltcOpt.isPresent() || ltcOpt.get().getLastCollectedDate() == null 
                    || ltcOpt.get().getLastCollectedDate().trim().isEmpty() 
                    || "Never".equalsIgnoreCase(ltcOpt.get().getLastCollectedDate())
                    || "N/A".equalsIgnoreCase(ltcOpt.get().getLastCollectedDate())) {
                result.put("status", "error");
                result.put("message", "Immediate waste collection request is unavailable because there is no collection history for your zone.");
                return result;
            } else {
                lastCollectedDate = parseLastCollectedDate(ltcOpt.get().getLastCollectedDate());
                daysSinceLastCollection = ChronoUnit.DAYS.between(lastCollectedDate, LocalDate.now());
            }

            // Check if the next waste collection schedule is within the next 4 days
            Optional<ZoneHouseDetail> houseOpt = zoneHouseDetailRepository.findById(userId);
            LocalDate nextCollectedDate = null;
            long daysUntilNextCollection = -999; // default negative value

            if (houseOpt.isPresent() && houseOpt.get().getNextCollectedDate() != null) {
                String nextCollectedDateStr = houseOpt.get().getNextCollectedDate();
                if (!"Not scheduled".equalsIgnoreCase(nextCollectedDateStr) 
                        && !"Pending assignment".equalsIgnoreCase(nextCollectedDateStr)
                        && !nextCollectedDateStr.trim().isEmpty()) {
                    nextCollectedDate = parseLastCollectedDate(nextCollectedDateStr);
                    daysUntilNextCollection = ChronoUnit.DAYS.between(LocalDate.now(), nextCollectedDate);
                }
            }

            boolean isWithinLast2Days = daysSinceLastCollection <= 2;
            boolean isNextWithin4Days = nextCollectedDate != null && daysUntilNextCollection >= 0 && daysUntilNextCollection <= 4;

            if (isWithinLast2Days) {
                result.put("status", "error");
                result.put("message", "Immediate waste collection request is unavailable because the last collection in your zone was within the last 2 days.");
                return result;
            }
            if (isNextWithin4Days) {
                result.put("status", "error");
                result.put("message", "Immediate waste collection request is unavailable because your next collection is already scheduled within the next 4 days.");
                return result;
            }

            // Determine fee: if days since last collection is less than 15, fee is ₹100. Otherwise default ₹50.
            double fee = 50.0;
            if (daysSinceLastCollection < 15) {
                fee = 100.0;
            }

            // Store request details
            String lastCollectedDateStr = (ltcOpt.isPresent() && ltcOpt.get().getLastCollectedDate() != null) 
                    ? ltcOpt.get().getLastCollectedDate() : null;
            
            String nextCollectedDateStr = null;
            if (houseOpt.isPresent() && houseOpt.get().getNextCollectedDate() != null) {
                String nd = houseOpt.get().getNextCollectedDate();
                if (!"Not scheduled".equalsIgnoreCase(nd) 
                        && !"Pending assignment".equalsIgnoreCase(nd)
                        && !nd.trim().isEmpty()) {
                    nextCollectedDateStr = nd;
                }
            }

            UserRequestedData request = new UserRequestedData();
            request.setId("REQ-" + UUID.randomUUID().toString().substring(0, 8));
            request.setUserId(userId);
            request.setZoneId(zoneId);
            request.setPaymentAmount(fee);
            request.setPaymentStatus("success");
            request.setRequestedDate(LocalDate.now().format(DATE_FMT));
            request.setLastCollectedDateInZone(lastCollectedDateStr);
            request.setNextScheduledDate(nextCollectedDateStr);
            request.setValidateOtp(null);
            request.setOtpGen(null);
            request.setStatus("pending");
            request.setWorkerId(null);

            userRequestedDataRepository.save(request);

            // Add points to user immediately upon request payment
            addPointsToUser(userId, fee, "Paid_immediate_pickup");

            result.put("status", "success");
            result.put("requestId", request.getId());
            result.put("fee", fee);
            result.put("message", "Immediate waste collection request registered successfully. Status: pending.");
            return result;
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    // ===========================
    // 3. WORKER ASSIGNMENT LOGIC (2 days before collection)
    // ===========================

    /**
     * Assign collection workers to zones 2 days before the scheduled date.
     * Worker must be from the same local body, not already assigned that day,
     * and not on approved leave.
     */
    public void assignWorkersForUpcomingCollections() {
        ZoningService.zoningLock.readLock().lock();
        try {
            LocalDate twoDaysFromNow = LocalDate.now().plusDays(2);
            String targetDate = twoDaysFromNow.format(DATE_FMT);
            assignWorkersForDate(targetDate);
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    public void assignWorkersForAllUpcoming() {
        ZoningService.zoningLock.readLock().lock();
        try {
            List<CollectionSchedule> schedules = collectionScheduleRepository.findAll();
            for (CollectionSchedule schedule : schedules) {
                if ("scheduled".equalsIgnoreCase(schedule.getStatus()) 
                        && (schedule.getCollectionWorkerAssigned() == null || schedule.getCollectionWorkerAssigned().isEmpty())) {
                    assignWorkersForDate(schedule.getScheduledDate());
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    public void assignWorkersForDate(String targetDate) {
        ZoningService.zoningLock.readLock().lock();
        try {
            List<CollectionSchedule> schedules = collectionScheduleRepository.findByScheduledDate(targetDate);
            if (schedules.isEmpty()) {
                return;
            }

            // 1. Determine the backup worker for each district for this date
            Map<String, String> backupWorkerByDistrict = new HashMap<>();
            List<String> districts = Arrays.asList("Kottayam", "Ernakulam");
            for (String dist : districts) {
                List<CollectionWorker> distWorkers = collectionWorkerRepository.findByDistrictAndRole(dist, "WORKER");
                if (distWorkers.size() >= 2) {
                    CollectionWorker backup = distWorkers.get(0);
                    backupWorkerByDistrict.put(dist, backup.getCollectionWorkerId());
                }
            }

            // 2. Assign workers to each schedule
            for (CollectionSchedule schedule : schedules) {
                if (schedule.getCollectionWorkerAssigned() != null && !schedule.getCollectionWorkerAssigned().isEmpty()) {
                    continue; // Already assigned
                }

                String localBody = schedule.getLocalBodyName();
                String district = schedule.getDistrict() != null ? schedule.getDistrict() : "Kottayam";
                String backupWorkerId = backupWorkerByDistrict.get(district);

                List<CollectionWorker> localWorkers = new ArrayList<>();
                if (localBody != null && !localBody.trim().isEmpty()) {
                    localWorkers = collectionWorkerRepository.findByLocalBodyNameAndRole(localBody.trim(), "WORKER");
                }

                CollectionWorker assignedWorker = null;

                // Step A: Find available local worker (excluding backup)
                for (CollectionWorker worker : localWorkers) {
                    String workerId = worker.getCollectionWorkerId();
                    if (workerId.equals(backupWorkerId)) {
                        continue; // Skip backup worker
                    }
                    if (isWorkerAvailable(workerId, targetDate)) {
                        assignedWorker = worker;
                        break;
                    }
                }

                // Step B: Fallback to any available worker in the same district (excluding backup)
                if (assignedWorker == null) {
                    List<CollectionWorker> distWorkers = collectionWorkerRepository.findByDistrictAndRole(district, "WORKER");
                    for (CollectionWorker worker : distWorkers) {
                        String workerId = worker.getCollectionWorkerId();
                        if (workerId.equals(backupWorkerId)) {
                            continue; // Skip backup worker
                        }
                        if (isWorkerAvailable(workerId, targetDate)) {
                            assignedWorker = worker;
                            break;
                        }
                    }
                }

                // Step C: Fallback to backup worker if staff is extremely short
                if (assignedWorker == null && backupWorkerId != null) {
                    if (isWorkerAvailable(backupWorkerId, targetDate)) {
                        Optional<CollectionWorker> backupOpt = collectionWorkerRepository.findById(backupWorkerId);
                        if (backupOpt.isPresent()) {
                            assignedWorker = backupOpt.get();
                        }
                    }
                }

                // Perform assignment if we found someone
                if (assignedWorker != null) {
                    String workerId = assignedWorker.getCollectionWorkerId();
                    schedule.setCollectionWorkerAssigned(workerId);
                    collectionScheduleRepository.save(schedule);

                    CollectionWorkerSchedule cws = collectionWorkerScheduleRepository.findById(workerId)
                            .orElse(new CollectionWorkerSchedule());
                    cws.setCollectionWorkerId(workerId);
                    cws.setName(assignedWorker.getName());
                    cws.setAssignedPanchayath(localBody);
                    cws.setAssignedZoneId(schedule.getZoneId());
                    cws.setNumberOfAssignedDates(cws.getNumberOfAssignedDates() + 1);
                    if (cws.getAssignedDates() == null) {
                        cws.setAssignedDates(new ArrayList<>());
                    }
                    if (!cws.getAssignedDates().contains(targetDate)) {
                        cws.getAssignedDates().add(targetDate);
                    }
                    collectionWorkerScheduleRepository.save(cws);
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    private boolean isWorkerAvailable(String workerId, String date) {
        List<CollectionSchedule> assignments = collectionScheduleRepository
                .findByCollectionWorkerAssignedAndScheduledDate(workerId, date);
        if (!assignments.isEmpty()) {
            return false;
        }

        List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
        boolean onLeave = leaves.stream()
                .anyMatch(l -> "approved".equalsIgnoreCase(l.getStatus())
                        && date.equals(l.getLeaveRequestedDate()));
        if (onLeave) {
            return false;
        }

        return true;
    }

    // ===========================
    // 4. OTP GENERATION
    // ===========================

    /**
     * Generate unique 4-digit OTP for each house in a zone on collection day.
     */
    public void generateOtpsForZone(String zoneId, String collectionDate) {
        ZoningService.zoningLock.readLock().lock();
        try {
            List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
            Random random = new Random();

            for (ZoneHouseDetail house : houses) {
                if (house.getOtp() == null || house.getOtp().trim().isEmpty()) {
                    String otp = String.format("%04d", random.nextInt(10000));
                    house.setOtp(otp);
                    house.setNextCollectedDate(collectionDate);
                    zoneHouseDetailRepository.save(house);

                    // Push notification to user
                    String houseUserId = house.getRegisteredUserId();
                    Notification notification = new Notification();
                    notification.setUserId(houseUserId);
                    notification.setTitle("Waste Collection OTP Generated");
                    notification.setMessage("Waste collection for your zone " + zoneId + " is scheduled for " + collectionDate + ". Your OTP for verification is: " + otp + ". Please share this OTP with the collection crew.");
                    notificationRepository.save(notification);
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    // ===========================
    // 5. VERIFY OTP & UPDATE COLLECTION STATUS
    // ===========================

    /**
     * Called by collection worker after verifying payment and OTP.
     * Updates collection status, clears OTP, updates payment records.
     */
    public Map<String, Object> verifyOtpAndCollect(String houseUserId, String enteredOtp,
            boolean paymentReceivedAtDoor) {
        ZoningService.zoningLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();

            Optional<ZoneHouseDetail> houseOpt = zoneHouseDetailRepository.findById(houseUserId);
            if (!houseOpt.isPresent()) {
                result.put("status", "error");
                result.put("message", "House not found");
                return result;
            }

            ZoneHouseDetail house = houseOpt.get();

            boolean isImmediatePending = "immediate_pending".equalsIgnoreCase(house.getCollectionStatus());
            // Verify date gate: check if current date is before the scheduled collection date
            String scheduledDateStr = house.getNextCollectedDate();
            if (scheduledDateStr != null && !scheduledDateStr.isEmpty()) {
                try {
                    LocalDate scheduledDate = LocalDate.parse(scheduledDateStr.trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    if (LocalDate.now().isBefore(scheduledDate)) {
                        result.put("status", "error");
                        result.put("message", "Waste collection cannot be verified before the scheduled collection date (" + scheduledDateStr + ")");
                        return result;
                    }
                } catch (Exception e) {
                    // Ignore parsing error or fallback safely
                }
            }

            // Verify OTP
            if (house.getOtp() == null || !house.getOtp().equals(enteredOtp)) {
                result.put("status", "error");
                result.put("message", "Invalid OTP");
                return result;
            }

            // Keep track of whether it was a dedicated immediate collection request is already stored in isImmediatePending

            // If payment was pending and received at door
            if (paymentReceivedAtDoor && house.getAmountPending() > 0) {
                // Update payment status
                Optional<RegisteredUserPayment> payOpt = registeredUserPaymentRepository.findById(houseUserId);
                if (payOpt.isPresent()) {
                    RegisteredUserPayment pay = payOpt.get();
                    pay.setAmountPending(0);
                    registeredUserPaymentRepository.save(pay);
                }
                addPointsToUser(houseUserId, house.getAmountPending(), "Paid_at_collection");
                house.setAmountPending(0);

                // Update worker's toPayAmount with the collection amount 50
                String zoneId = house.getZoneId();
                List<CollectionSchedule> schedules = collectionScheduleRepository.findByZoneId(zoneId);
                String workerId = null;
                String targetDate = house.getNextCollectedDate();
                if (targetDate != null) {
                    for (CollectionSchedule s : schedules) {
                        if (targetDate.equals(s.getScheduledDate())) {
                            workerId = s.getCollectionWorkerAssigned();
                            break;
                        }
                    }
                }
                if (workerId == null) {
                    for (CollectionSchedule s : schedules) {
                        if ("scheduled".equalsIgnoreCase(s.getStatus()) || "in_progress".equalsIgnoreCase(s.getStatus())) {
                            workerId = s.getCollectionWorkerAssigned();
                            break;
                        }
                    }
                }
                if (workerId != null) {
                    Optional<CollectionWorker> workerOpt = collectionWorkerRepository.findById(workerId);
                    if (workerOpt.isPresent()) {
                        CollectionWorker worker = workerOpt.get();
                        double currentToPay = worker.getToPayAmount() != null ? worker.getToPayAmount() : 0.0;
                        worker.setToPayAmount(currentToPay + 50.0);
                        collectionWorkerRepository.save(worker);
                    }
                }
            }

            // Mark as collected
            house.setCollectionStatus("collected");
            String nextCollectedDate = house.getNextCollectedDate();
            List<String> lastDates = house.getLastCollectedDate();
            if (lastDates == null) {
                lastDates = new ArrayList<>();
            }
            lastDates.add(0, nextCollectedDate);
            if (lastDates.size() > 10) {
                lastDates = new ArrayList<>(lastDates.subList(0, 10));
            }
            house.setLastCollectedDate(lastDates);
            house.setOtp(null); // Clear OTP

            // Update user collection history
            Optional<RegisteredUserCollection> rucOpt = registeredUserCollectionRepository.findById(houseUserId);
            if (rucOpt.isPresent()) {
                RegisteredUserCollection ruc = rucOpt.get();
                ruc.setLastCollectionStatus("collected");
                addToCollectionHistory(ruc, nextCollectedDate);
                registeredUserCollectionRepository.save(ruc);
            }

            // If it was immediate, sync next collection date back to the zone's scheduled date
            if (isImmediatePending) {
                List<CollectionSchedule> zoneSchedules = collectionScheduleRepository.findByZoneId(house.getZoneId());
                String zoneScheduledDate = "Not scheduled";
                for (CollectionSchedule s : zoneSchedules) {
                    if ("scheduled".equalsIgnoreCase(s.getStatus())) {
                        zoneScheduledDate = s.getScheduledDate();
                        break;
                    }
                }
                house.setNextCollectedDate(zoneScheduledDate);
                if (rucOpt.isPresent()) {
                    RegisteredUserCollection ruc = rucOpt.get();
                    ruc.setNextScheduledDate(zoneScheduledDate);
                    registeredUserCollectionRepository.save(ruc);
                }
            }

            zoneHouseDetailRepository.save(house);

            // Sync UserRequestedData status if there is an accepted or scheduled request
            List<UserRequestedData> reqs = userRequestedDataRepository.findByUserId(houseUserId);
            if (reqs != null) {
                for (UserRequestedData req : reqs) {
                    if ("accepted".equalsIgnoreCase(req.getStatus()) || "scheduled".equalsIgnoreCase(req.getStatus())) {
                        req.setStatus("success");
                        userRequestedDataRepository.save(req);
                    }
                }
            }

            result.put("status", "success");
            result.put("message", "Waste collected successfully from house: " + houseUserId);
            return result;
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    // ===========================
    // 6. MARK ZONE COLLECTION COMPLETE
    // ===========================

    /**
     * After all houses in a zone are collected, update LastTimeCollection.
     */
    public void markZoneCollectionComplete(String zoneId, String collectionDate) {
        ZoningService.zoningLock.readLock().lock();
        try {
            Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
            LastTimeCollection ltc;
            if (ltcOpt.isPresent()) {
                ltc = ltcOpt.get();
            } else {
                ltc = new LastTimeCollection();
                ltc.setZoneId(zoneId);
                ltc.setPrevious10Dates(new ArrayList<>());
            }

            // Shift current last_collected_date into previous_10 history
            if (ltc.getLastCollectedDate() != null) {
                ltc.getPrevious10Dates().add(0, ltc.getLastCollectedDate());
                if (ltc.getPrevious10Dates().size() > 10) {
                    ltc.setPrevious10Dates(ltc.getPrevious10Dates().subList(0, 10));
                }
            }

            ltc.setLastCollectedDate(collectionDate);
            lastTimeCollectionRepository.save(ltc);

            // Update schedule status
            List<CollectionSchedule> schedules = collectionScheduleRepository.findByZoneId(zoneId);
            for (CollectionSchedule s : schedules) {
                if (collectionDate.equals(s.getScheduledDate())) {
                    s.setStatus("completed");
                    collectionScheduleRepository.save(s);
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    // ===========================
    // HELPER METHODS
    // ===========================

    private void updateZoneHousesForSchedule(String zoneId, String scheduledDate, double amount) {
        updateZoneHousesForSchedule(zoneId, scheduledDate, amount, null, 0.0);
    }

    private void updateZoneHousesForSchedule(String zoneId, String scheduledDate, double normalAmount, String requesterId, double requesterAmount) {
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        Random random = new Random();
        for (ZoneHouseDetail house : houses) {
            house.setNextCollectedDate(scheduledDate);
            double amt = (requesterId != null && requesterId.equals(house.getRegisteredUserId())) ? requesterAmount : normalAmount;
            house.setAmountPending(amt);
            house.setCollectionStatus("pending");
            
            // Create a unique 4-digit OTP for each house
            String otp = String.format("%04d", random.nextInt(10000));
            house.setOtp(otp);
            zoneHouseDetailRepository.save(house);

            // Send this OTP to the user's notification list
            String houseUserId = house.getRegisteredUserId();
            Notification notification = new Notification();
            notification.setUserId(houseUserId);
            notification.setTitle("Waste Collection OTP Generated");
            notification.setMessage("Waste collection for your zone " + zoneId + " has been scheduled for " + scheduledDate + ". Your OTP for verification is: " + otp + ". Please share this OTP with the collection crew.");
            notificationRepository.save(notification);
        }

        // Also update registered_user_collection and registered_user_payment
        List<RegisteredUser> users = registeredUserRepository.findByZoneId(zoneId);
        for (RegisteredUser user : users) {
            String uid = user.getHouseId();

            // Update collection record
            RegisteredUserCollection ruc = registeredUserCollectionRepository.findById(uid)
                    .orElse(new RegisteredUserCollection());
            ruc.setUserId(uid);
            ruc.setNextScheduledDate(scheduledDate);
            registeredUserCollectionRepository.save(ruc);

            // Update payment record — set amount pending
            RegisteredUserPayment rup = registeredUserPaymentRepository.findById(uid)
                    .orElse(new RegisteredUserPayment());
            rup.setUserId(uid);
            double amt = (requesterId != null && requesterId.equals(uid)) ? requesterAmount : normalAmount;
            rup.setAmountPending(rup.getAmountPending() + amt);
            registeredUserPaymentRepository.save(rup);

            // Synchronize pending payment in registered_users table
            user.setPendingPayment(user.getPendingPayment() + amt);
            registeredUserRepository.save(user);
        }
    }

    public void scheduleAllZones() {
        ZoningService.zoningLock.readLock().lock();
        try {
            // Fetch all zone IDs from ZoneKottayam and ZoneErnakulam
            List<ZoneKottayam> kotZones = zoneKottayamRepository.findAll();
            List<ZoneErnakulam> ernZones = zoneErnakulamRepository.findAll();

            List<String> allZoneIds = new ArrayList<>();
            for (ZoneKottayam z : kotZones) {
                allZoneIds.add(z.getZoneId());
            }
            for (ZoneErnakulam z : ernZones) {
                allZoneIds.add(z.getZoneId());
            }

            for (String zoneId : allZoneIds) {
                // Check if already scheduled
                List<CollectionSchedule> activeSchedules = collectionScheduleRepository.findByZoneId(zoneId);
                boolean alreadyScheduled = activeSchedules.stream()
                        .anyMatch(s -> "scheduled".equalsIgnoreCase(s.getStatus()));
                if (alreadyScheduled) {
                    continue;
                }

                // Check LastTimeCollection history
                Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
                if (ltcOpt.isPresent()) {
                    LastTimeCollection ltc = ltcOpt.get();
                    LocalDate lastCollected = parseLastCollectedDate(ltc.getLastCollectedDate());
                    long daysSince = ChronoUnit.DAYS.between(lastCollected, LocalDate.now());
                    if (daysSince >= 15) {
                        generateScheduleForZone(zoneId);
                    }
                } else {
                    // Initialize it and generate schedule immediately
                    initializeLastTimeCollection(zoneId);
                    generateScheduleForZone(zoneId);
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    private void initializeLastTimeCollection(String zoneId) {
        if (!lastTimeCollectionRepository.existsById(zoneId)) {
            LastTimeCollection ltc = new LastTimeCollection();
            ltc.setZoneId(zoneId);
            ltc.setLastCollectedDate(LocalDate.now().minusDays(30).format(DATE_FMT));
            ltc.setPrevious10Dates(new ArrayList<>());
            lastTimeCollectionRepository.save(ltc);
        }
    }

    private void updateUserPaymentAmount(String userId, double amount) {
        RegisteredUserPayment rup = registeredUserPaymentRepository.findById(userId)
                .orElse(new RegisteredUserPayment());
        rup.setUserId(userId);
        rup.setAmountPending(rup.getAmountPending() + amount);
        registeredUserPaymentRepository.save(rup);
    }

    private void addToPaymentHistory(RegisteredUserPayment pay, String entry) {
        List<String> history = pay.getLast10TimesDetails();
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(0, entry);
        if (history.size() > 10) {
            history = new ArrayList<>(history.subList(0, 10));
        }
        pay.setLast10TimesDetails(history);
    }

    private void addToCollectionHistory(RegisteredUserCollection ruc, String date) {
        List<String> history = ruc.getLast10TimeWasteCollectedDate();
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(0, date);
        if (history.size() > 10) {
            history = new ArrayList<>(history.subList(0, 10));
        }
        ruc.setLast10TimeWasteCollectedDate(history);
    }

    public void addPointsToUser(String userId, double amountPaid, String paymentDetail) {
        int pointsEarned = (int) (amountPaid / 5);
        if (pointsEarned <= 0) return;

        // 1. Update RegisteredUser points
        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
        if (userOpt.isPresent()) {
            RegisteredUser user = userOpt.get();
            user.setPoints(user.getPoints() + pointsEarned);
            registeredUserRepository.save(user);
        }

        // 2. Update RegisteredUserPayment points and history
        RegisteredUserPayment pay = registeredUserPaymentRepository.findById(userId)
                .orElse(new RegisteredUserPayment(userId, 0.0, new ArrayList<>(), 0, 0));
        pay.setPointEarned(pay.getPointEarned() + pointsEarned);
        pay.setPendingPoint(pay.getPendingPoint() + pointsEarned);
        
        List<String> history = pay.getLast10TimesDetails();
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(0, paymentDetail);
        if (history.size() > 10) {
            history = new ArrayList<>(history.subList(0, 10));
        }
        pay.setLast10TimesDetails(history);
        
        registeredUserPaymentRepository.save(pay);
    }

    public Map<String, Object> assignOtpsToAllScheduledZones() {
        ZoningService.zoningLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();
            List<CollectionSchedule> schedules = collectionScheduleRepository.findAll();
            int otpCount = 0;
            Random random = new Random();

            for (CollectionSchedule schedule : schedules) {
                if ("scheduled".equalsIgnoreCase(schedule.getStatus())) {
                    String zoneId = schedule.getZoneId();
                    String scheduledDate = schedule.getScheduledDate();

                    List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
                    for (ZoneHouseDetail house : houses) {
                        // Check if house has no OTP
                        if (house.getOtp() == null || house.getOtp().trim().isEmpty()) {
                            String otp = String.format("%04d", random.nextInt(10000));
                            house.setOtp(otp);
                            house.setNextCollectedDate(scheduledDate);
                            zoneHouseDetailRepository.save(house);

                            // Push notification
                            String houseUserId = house.getRegisteredUserId();
                            Notification notification = new Notification();
                            notification.setUserId(houseUserId);
                            notification.setTitle("Waste Collection OTP Generated");
                            notification.setMessage("Waste collection for your zone " + zoneId + " is scheduled for " + scheduledDate + ". Your OTP for verification is: " + otp + ". Please share this OTP with the collection crew.");
                            notificationRepository.save(notification);

                            otpCount++;
                        }
                    }
                }
            }

            result.put("status", "success");
            result.put("message", "Successfully generated OTPs for " + otpCount + " households without existing OTPs.");
            return result;
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    public Map<String, Object> skipHouse(String houseUserId, String reason) {
        ZoningService.zoningLock.readLock().lock();
        try {
            Map<String, Object> result = new HashMap<>();
            Optional<ZoneHouseDetail> houseOpt = zoneHouseDetailRepository.findById(houseUserId);
            if (!houseOpt.isPresent()) {
                result.put("status", "error");
                result.put("message", "House not found");
                return result;
            }

            ZoneHouseDetail house = houseOpt.get();
            // Check payment status: paid if amountPending <= 0
            if (house.getAmountPending() > 0) {
                // If not paid, set status to skipped but do not reschedule for tomorrow
                house.setCollectionStatus("skipped");
                zoneHouseDetailRepository.save(house);
                result.put("status", "success");
                result.put("message", "House skipped. Since payment status is pending, it was not rescheduled for tomorrow.");
                return result;
            }

            // If paid:
            String tomorrowStr = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            
            // 1. Update house details
            house.setNextCollectedDate(tomorrowStr);
            house.setCollectionStatus("pending");
            
            // Generate OTP
            Random random = new Random();
            String otp = String.format("%04d", random.nextInt(10000));
            house.setOtp(otp);
            zoneHouseDetailRepository.save(house);

            // 2. Find or create schedule for tomorrow
            String zoneId = house.getZoneId();
            List<CollectionSchedule> existingTomorrowSchedules = collectionScheduleRepository.findByZoneId(zoneId);
            CollectionSchedule tomorrowSchedule = null;
            for (CollectionSchedule cs : existingTomorrowSchedules) {
                if (tomorrowStr.equals(cs.getScheduledDate())) {
                    tomorrowSchedule = cs;
                    break;
                }
            }

            String assignedWorkerId = null;
            String assignedWorkerName = "Worker";

            if (tomorrowSchedule != null) {
                assignedWorkerId = tomorrowSchedule.getCollectionWorkerAssigned();
                Optional<CollectionWorker> cwOpt = collectionWorkerRepository.findById(assignedWorkerId);
                if (cwOpt.isPresent()) {
                    assignedWorkerName = cwOpt.get().getName();
                }
            } else {
                // We need to find a free worker or randomly assign one
                List<CollectionWorker> workers = collectionWorkerRepository.findByRole("WORKER");
                // Find a free worker
                for (CollectionWorker worker : workers) {
                    String wId = worker.getCollectionWorkerId();
                    if (isWorkerAvailableForDate(wId, tomorrowStr)) {
                        assignedWorkerId = wId;
                        assignedWorkerName = worker.getName();
                        break;
                    }
                }

                // If no worker is free, randomly assign one
                if (assignedWorkerId == null && !workers.isEmpty()) {
                    CollectionWorker randomWorker = workers.get(random.nextInt(workers.size()));
                    assignedWorkerId = randomWorker.getCollectionWorkerId();
                    assignedWorkerName = randomWorker.getName();
                }

                if (assignedWorkerId != null) {
                    // Create a tomorrow schedule for this zone
                    tomorrowSchedule = new CollectionSchedule();
                    String scheduleId = "SCH-" + zoneId + "-" + UUID.randomUUID().toString().substring(0, 6);
                    tomorrowSchedule.setScheduleId(scheduleId);
                    tomorrowSchedule.setZoneId(zoneId);
                    tomorrowSchedule.setScheduledDate(tomorrowStr);
                    tomorrowSchedule.setCollectionWorkerAssigned(assignedWorkerId);
                    tomorrowSchedule.setStatus("scheduled");
                    
                    // Copy localBodyName and district from any existing schedule of this zone
                    if (!existingTomorrowSchedules.isEmpty()) {
                        CollectionSchedule template = existingTomorrowSchedules.get(0);
                        tomorrowSchedule.setLocalBodyName(template.getLocalBodyName());
                        tomorrowSchedule.setDistrict(template.getDistrict());
                    } else {
                        // Fallback to worker body/district
                        Optional<CollectionWorker> cwOpt = collectionWorkerRepository.findById(assignedWorkerId);
                        if (cwOpt.isPresent()) {
                            tomorrowSchedule.setLocalBodyName(cwOpt.get().getLocalBodyName());
                            tomorrowSchedule.setDistrict(cwOpt.get().getDistrict());
                        }
                    }
                    collectionScheduleRepository.save(tomorrowSchedule);

                    // Update worker schedule record
                    updateWorkerScheduleRecordForDate(assignedWorkerId, assignedWorkerName, zoneId, tomorrowStr);
                }
            }

            // Also update RegisteredUserCollection if it exists
            Optional<RegisteredUserCollection> rucOpt = registeredUserCollectionRepository.findById(houseUserId);
            if (rucOpt.isPresent()) {
                RegisteredUserCollection ruc = rucOpt.get();
                ruc.setNextScheduledDate(tomorrowStr);
                ruc.setLastCollectionStatus("pending");
                registeredUserCollectionRepository.save(ruc);
            }

            // 3. Notify the resident user
            Notification notification = new Notification();
            notification.setUserId(houseUserId);
            notification.setTitle("Waste Collection Rescheduled (Skipped)");
            notification.setMessage("Your waste collection was skipped today but has been rescheduled for tomorrow (" + tomorrowStr + "). Your OTP is: " + otp + ". Assigned worker: " + assignedWorkerName + ".");
            notificationRepository.save(notification);

            result.put("status", "success");
            result.put("message", "House was skipped. Rescheduled for tomorrow (" + tomorrowStr + ") with worker " + assignedWorkerName + ".");
            return result;

        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    private boolean isWorkerAvailableForDate(String workerId, String date) {
        List<CollectionSchedule> assignments = collectionScheduleRepository.findByCollectionWorkerAssignedAndScheduledDate(workerId, date);
        if (!assignments.isEmpty()) {
            return false;
        }

        List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
        boolean onLeave = leaves.stream()
                .anyMatch(l -> "approved".equalsIgnoreCase(l.getStatus())
                        && date.equals(l.getLeaveRequestedDate()));
        if (onLeave) {
            return false;
        }

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

    private void updateWorkerScheduleRecordForDate(String workerId, String workerName, String zoneId, String date) {
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

    public void closeOverduePendingHouses() {
        ZoningService.zoningLock.readLock().lock();
        try {
            List<LastTimeCollection> lastTimeCollections = lastTimeCollectionRepository.findAll();
            LocalDate today = LocalDate.now();

            for (LastTimeCollection ltc : lastTimeCollections) {
                String zoneId = ltc.getZoneId();
                if (ltc.getLastCollectedDate() == null || ltc.getLastCollectedDate().trim().isEmpty()) {
                    continue;
                }

                LocalDate lastCollectedDate = parseLastCollectedDate(ltc.getLastCollectedDate());
                long daysSince = ChronoUnit.DAYS.between(lastCollectedDate, today);

                // If more than 2 days have passed since the last collection
                if (daysSince > 2) {
                    List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
                    
                    // Fetch the zone's next scheduled date (if any)
                    List<CollectionSchedule> schedules = collectionScheduleRepository.findByZoneId(zoneId);
                    String zoneScheduledDate = null;
                    for (CollectionSchedule s : schedules) {
                        if ("scheduled".equalsIgnoreCase(s.getStatus())) {
                            zoneScheduledDate = s.getScheduledDate();
                            break;
                        }
                    }

                    for (ZoneHouseDetail house : houses) {
                        String status = house.getCollectionStatus();
                        if (status != null && ("pending".equalsIgnoreCase(status) || "immediate_pending".equalsIgnoreCase(status))) {
                            house.setCollectionStatus("closed");
                            if (zoneScheduledDate != null) {
                                house.setNextCollectedDate(zoneScheduledDate);
                            } else {
                                house.setNextCollectedDate("Pending assignment");
                            }
                            house.setOtp(null);
                            zoneHouseDetailRepository.save(house);

                            // Also update RegisteredUserCollection if it exists
                            Optional<RegisteredUserCollection> rucOpt = registeredUserCollectionRepository.findById(house.getRegisteredUserId());
                            if (rucOpt.isPresent()) {
                                RegisteredUserCollection ruc = rucOpt.get();
                                ruc.setLastCollectionStatus("closed");
                                if (zoneScheduledDate != null) {
                                    ruc.setNextScheduledDate(zoneScheduledDate);
                                } else {
                                    ruc.setNextScheduledDate("Pending assignment");
                                }
                                registeredUserCollectionRepository.save(ruc);
                            }
                        }
                    }
                }
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    private LocalDate parseLastCollectedDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now().minusDays(30);
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FMT);
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ex) {
                return LocalDate.now().minusDays(30);
            }
        }
    }
}
