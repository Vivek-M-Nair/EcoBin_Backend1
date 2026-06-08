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
 * 1. In the same panchayath, only 2 concurrent wards share the same scheduled date.
 * 2. After collection, next date is scheduled only after 15 days.
 * 3. After 15 days, backend assigns a new date 24-28 days from last collected date.
 * 4. Normal amount = ₹50 per house.
 * 5. Immediate request (after 15 days) reschedules zone to 17-21 days, requester pays ₹60, others ₹50.
 * 6. Immediate request (before 15 days) assigns dedicated worker, requester pays ₹100, date 1-2 days out.
 */
@Service
public class SchedulingService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final double NORMAL_AMOUNT = 50.0;
    private static final double IMMEDIATE_AFTER_15_AMOUNT = 60.0;
    private static final double IMMEDIATE_BEFORE_15_AMOUNT = 100.0;

    @Autowired private LastTimeCollectionRepository lastTimeCollectionRepository;
    @Autowired private CollectionScheduleRepository collectionScheduleRepository;
    @Autowired private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired private RegisteredUserCollectionRepository registeredUserCollectionRepository;
    @Autowired private RegisteredUserPaymentRepository registeredUserPaymentRepository;
    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private ImmediateCollectionReasonRepository immediateCollectionReasonRepository;
    @Autowired private CollectionWorkerRepository collectionWorkerRepository;
    @Autowired private CollectionWorkerScheduleRepository collectionWorkerScheduleRepository;
    @Autowired private CollectionScheduleRepository scheduleRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;

    // ===========================
    // 1. GENERATE SCHEDULE FOR ZONE (called by scheduler or manually)
    // ===========================

    /**
     * After 15 days from last collection, assign new date between 24-28 days from last collected date.
     */
    public Map<String, Object> generateScheduleForZone(String zoneId) {
        Map<String, Object> result = new HashMap<>();

        Optional<LastTimeCollection> ltcOpt = lastTimeCollectionRepository.findById(zoneId);
        if (!ltcOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "No collection history found for zone: " + zoneId);
            return result;
        }

        LastTimeCollection ltc = ltcOpt.get();
        LocalDate lastCollected = LocalDate.parse(ltc.getLastCollectedDate(), DATE_FMT);
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
    }

    // ===========================
    // 2. IMMEDIATE COLLECTION REQUEST
    // ===========================

    /**
     * Handle immediate collection request from a registered user.
     * - If > 15 days since last collection OR date already scheduled: reschedule zone to 17-21 days.
     *   Requester pays ₹60, others pay ₹50.
     * - If < 15 days since last collection: assign dedicated worker, requester pays ₹100.
     *   Next date for requester = 1-2 days from now. After collection, syncs back to zone schedule.
     */
    public Map<String, Object> handleImmediateCollectionRequest(String userId, String reason) {
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
            lastCollected = LocalDate.parse(ltcOpt.get().getLastCollectedDate(), DATE_FMT);
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
        LocalDate twoDaysFromNow = LocalDate.now().plusDays(2);
        String targetDate = twoDaysFromNow.format(DATE_FMT);

        List<CollectionSchedule> schedules = collectionScheduleRepository.findByScheduledDate(targetDate);

        for (CollectionSchedule schedule : schedules) {
            if (schedule.getCollectionWorkerAssigned() != null && !schedule.getCollectionWorkerAssigned().isEmpty()) {
                continue; // Already assigned
            }

            String localBody = schedule.getLocalBodyName();
            String district = schedule.getDistrict();

            // Find available workers in the same local body
            List<CollectionWorker> workers;
            if (localBody != null) {
                workers = collectionWorkerRepository.findByLocalBodyNameAndRole(localBody, "WORKER");
            } else {
                workers = collectionWorkerRepository.findByDistrictAndRole(
                        district != null ? district : "Kottayam", "WORKER");
            }

            for (CollectionWorker worker : workers) {
                String workerId = worker.getCollectionWorkerId();

                // Check if already assigned on this date
                List<CollectionSchedule> workerAssignments =
                        collectionScheduleRepository.findByCollectionWorkerAssignedAndScheduledDate(workerId, targetDate);
                if (!workerAssignments.isEmpty()) {
                    continue; // Already assigned elsewhere
                }

                // Check if on approved leave
                List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
                boolean onLeave = leaves.stream()
                        .anyMatch(l -> "approved".equalsIgnoreCase(l.getStatus())
                                && targetDate.equals(l.getLeaveRequestedDate()));
                if (onLeave) {
                    continue;
                }

                // Assign this worker
                schedule.setCollectionWorkerAssigned(workerId);
                collectionScheduleRepository.save(schedule);

                // Update worker schedule
                CollectionWorkerSchedule cws = collectionWorkerScheduleRepository.findById(workerId)
                        .orElse(new CollectionWorkerSchedule());
                cws.setCollectionWorkerId(workerId);
                cws.setName(worker.getName());
                cws.setAssignedPanchayath(localBody);
                cws.setAssignedZoneId(schedule.getZoneId());
                cws.setNumberOfAssignedDates(cws.getNumberOfAssignedDates() + 1);
                if (cws.getAssignedDates() == null) {
                    cws.setAssignedDates(new ArrayList<>());
                }
                cws.getAssignedDates().add(targetDate);
                collectionWorkerScheduleRepository.save(cws);
                break;
            }
        }
    }

    // ===========================
    // 4. OTP GENERATION
    // ===========================

    /**
     * Generate unique 4-digit OTP for each house in a zone on collection day.
     */
    public void generateOtpsForZone(String zoneId, String collectionDate) {
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        Random random = new Random();

        for (ZoneHouseDetail house : houses) {
            if (collectionDate.equals(house.getNextCollectedDate())) {
                String otp = String.format("%04d", random.nextInt(10000));
                house.setOtp(otp);
                zoneHouseDetailRepository.save(house);
            }
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
        house.setLastCollectedDate(house.getNextCollectedDate());
        house.setOtp(null); // Clear OTP
        zoneHouseDetailRepository.save(house);

        // Update user collection history
        Optional<RegisteredUserCollection> rucOpt = registeredUserCollectionRepository.findById(houseUserId);
        if (rucOpt.isPresent()) {
            RegisteredUserCollection ruc = rucOpt.get();
            ruc.setLastCollectionStatus("collected");
            addToCollectionHistory(ruc, house.getLastCollectedDate());
            registeredUserCollectionRepository.save(ruc);
        }

        result.put("status", "success");
        result.put("message", "Waste collected successfully from house: " + houseUserId);
        return result;
    }

    // ===========================
    // 6. MARK ZONE COLLECTION COMPLETE
    // ===========================

    /**
     * After all houses in a zone are collected, update LastTimeCollection.
     */
    public void markZoneCollectionComplete(String zoneId, String collectionDate) {
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
    }

    // ===========================
    // HELPER METHODS
    // ===========================

    private void updateZoneHousesForSchedule(String zoneId, String scheduledDate, double amount) {
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        for (ZoneHouseDetail house : houses) {
            house.setNextCollectedDate(scheduledDate);
            house.setAmountPending(amount);
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
            rup.setAmountPending(rup.getAmountPending() + amount);
            registeredUserPaymentRepository.save(rup);
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

    /**
     * Points calculation: 10 points for every ₹50 paid.
     */
    private void calculateAndAddPoints(RegisteredUserPayment pay, double amountPaid) {
        int pointsEarned = (int) (amountPaid / 5); // 10 points per ₹50
        pay.setPointEarned(pay.getPointEarned() + pointsEarned);
        pay.setPendingPoint(pay.getPendingPoint() + pointsEarned);
    }
}
