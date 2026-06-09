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

            // Store reason if provided
            if (reason != null && !reason.trim().isEmpty()) {
                ImmediateCollectionReason icr = new ImmediateCollectionReason();
                icr.setUserId(userId);
                icr.setReason(reason);
                icr.setRequestedDate(LocalDate.now().format(DATE_FMT));
                immediateCollectionReasonRepository.save(icr);
            }

            // Check last collection date for this zone
            Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
            LocalDate lastCollected;
            long daysSinceCollection;

            if (ltcOpt.isPresent()) {
                lastCollected = parseLastCollectedDate(ltcOpt.get().getLastCollectedDate());
                daysSinceCollection = ChronoUnit.DAYS.between(lastCollected, LocalDate.now());
            } else {
                // No history; treat as > 15 days
                lastCollected = LocalDate.now().minusDays(30);
                daysSinceCollection = 30;
            }

            // Check if a new date is already scheduled for this zone
            List<CollectionSchedule> existingSchedules = collectionScheduleRepository.findByZoneId(zoneId);
            boolean dateAlreadyScheduled = existingSchedules.stream()
                    .anyMatch(s -> "scheduled".equals(s.getStatus()));

            if (daysSinceCollection > 15 || dateAlreadyScheduled) {
                // CASE A: Reschedule zone to 17-21 days from last collection
                return handleImmediateAfter15Days(userId, zoneId, lastCollected, dateAlreadyScheduled);
            } else {
                // CASE B: Before 15 days — assign dedicated worker
                return handleImmediateBefore15Days(userId, zoneId, user);
            }
        } finally {
            ZoningService.zoningLock.readLock().unlock();
        }
    }

    private Map<String, Object> handleImmediateAfter15Days(String userId, String zoneId,
            LocalDate lastCollected,
            boolean dateAlreadyScheduled) {
        Map<String, Object> result = new HashMap<>();

        // Reschedule to 17-21 days from last collection
        int randomDays = 17 + new Random().nextInt(5); // 17 to 21
        LocalDate newDate = lastCollected.plusDays(randomDays);
        LocalDate today = LocalDate.now();
        if (newDate.isBefore(today)) {
            newDate = today.plusDays(1);
        }
        String newDateStr = newDate.format(DATE_FMT);

        // Update or create schedule
        if (dateAlreadyScheduled) {
            List<CollectionSchedule> schedules = collectionScheduleRepository.findByZoneId(zoneId);
            for (CollectionSchedule s : schedules) {
                if ("scheduled".equals(s.getStatus())) {
                    s.setScheduledDate(newDateStr);
                    collectionScheduleRepository.save(s);
                    break;
                }
            }
        } else {
            String scheduleId = "SCH-" + zoneId + "-" + UUID.randomUUID().toString().substring(0, 6);
            CollectionSchedule schedule = new CollectionSchedule();
            schedule.setScheduleId(scheduleId);
            schedule.setZoneId(zoneId);
            schedule.setScheduledDate(newDateStr);
            schedule.setAmountPerHouse(NORMAL_AMOUNT);
            schedule.setStatus("scheduled");
            collectionScheduleRepository.save(schedule);
        }

        // Update all houses in zone: ₹50 for everyone
        updateZoneHousesForSchedule(zoneId, newDateStr, NORMAL_AMOUNT);

        // Override amount for the requester: ₹60
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        for (ZoneHouseDetail h : houses) {
            if (userId.equals(h.getRegisteredUserId())) {
                h.setAmountPending(IMMEDIATE_AFTER_15_AMOUNT);
                zoneHouseDetailRepository.save(h);
                break;
            }
        }

        // Update requester's payment record
        updateUserPaymentAmount(userId, IMMEDIATE_AFTER_15_AMOUNT);

        result.put("status", "rescheduled");
        result.put("newScheduledDate", newDateStr);
        result.put("yourAmount", IMMEDIATE_AFTER_15_AMOUNT);
        result.put("message", "Zone rescheduled. Your amount is ₹60, others ₹50.");
        return result;
    }

    private Map<String, Object> handleImmediateBefore15Days(String userId, String zoneId, RegisteredUser user) {
        Map<String, Object> result = new HashMap<>();

        // Assign a dedicated worker for this single house
        // Date is 1-2 days from now
        int daysOut = 1 + new Random().nextInt(2); // 1 or 2
        LocalDate collectionDate = LocalDate.now().plusDays(daysOut);
        String collectionDateStr = collectionDate.format(DATE_FMT);

        // Update the user's registered_user_collection
        RegisteredUserCollection ruc = registeredUserCollectionRepository.findById(userId)
                .orElse(new RegisteredUserCollection());
        ruc.setUserId(userId);
        ruc.setNextScheduledDate(collectionDateStr);
        ruc.setLastCollectionStatus("immediate_pending");
        registeredUserCollectionRepository.save(ruc);

        // Update zone house detail
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        for (ZoneHouseDetail h : houses) {
            if (userId.equals(h.getRegisteredUserId())) {
                h.setNextCollectedDate(collectionDateStr);
                h.setAmountPending(IMMEDIATE_BEFORE_15_AMOUNT);
                h.setCollectionStatus("immediate_pending");
                zoneHouseDetailRepository.save(h);
                break;
            }
        }

        // Update payment
        updateUserPaymentAmount(userId, IMMEDIATE_BEFORE_15_AMOUNT);

        result.put("status", "immediate_assigned");
        result.put("collectionDate", collectionDateStr);
        result.put("amount", IMMEDIATE_BEFORE_15_AMOUNT);
        result.put("message", "A collection staff will be assigned to your house. Amount: ₹100. " +
                "After collection, your schedule will sync back to your zone schedule.");
        return result;
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
                if (collectionDate.equals(house.getNextCollectedDate())) {
                    String otp = String.format("%04d", random.nextInt(10000));
                    house.setOtp(otp);
                    zoneHouseDetailRepository.save(house);
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

            // Verify OTP
            if (house.getOtp() == null || !house.getOtp().equals(enteredOtp)) {
                result.put("status", "error");
                result.put("message", "Invalid OTP");
                return result;
            }

            // Keep track of whether it was a dedicated immediate collection request
            boolean isImmediatePending = "immediate_pending".equalsIgnoreCase(house.getCollectionStatus());

            // If payment was pending and received at door
            if (paymentReceivedAtDoor && house.getAmountPending() > 0) {
                // Update payment status
                Optional<RegisteredUserPayment> payOpt = registeredUserPaymentRepository.findById(houseUserId);
                if (payOpt.isPresent()) {
                    RegisteredUserPayment pay = payOpt.get();
                    pay.setAmountPending(0);
                    addToPaymentHistory(pay, "Paid_at_collection");
                    calculateAndAddPoints(pay, house.getAmountPending());
                    registeredUserPaymentRepository.save(pay);
                }
                house.setAmountPending(0);
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
        for (ZoneHouseDetail house : houses) {
            house.setNextCollectedDate(scheduledDate);
            double amt = (requesterId != null && requesterId.equals(house.getRegisteredUserId())) ? requesterAmount : normalAmount;
            house.setAmountPending(amt);
            house.setCollectionStatus("pending");
            zoneHouseDetailRepository.save(house);
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

    private void calculateAndAddPoints(RegisteredUserPayment pay, double amountPaid) {
        int pointsEarned = (int) (amountPaid / 5); // 10 points per ₹50
        pay.setPointEarned(pay.getPointEarned() + pointsEarned);
        pay.setPendingPoint(pay.getPendingPoint() + pointsEarned);
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
