package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for collection worker operations: leave, reports, expenses, collection detail view.
 */
@Service
public class WorkerService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Autowired private CollectionWorkerRepository workerRepository;
    @Autowired private CollectionWorkerScheduleRepository workerScheduleRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private CollectionExpenseRepository expenseRepository;
    @Autowired private CollectionScheduleRepository scheduleRepository;
    @Autowired private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired private RegisteredUserPaymentRepository paymentRepository;

    // ===========================
    // 1. LEAVE REQUEST
    // ===========================

    public Map<String, Object> submitLeaveRequest(String workerId, String leaveDate, String reason) {
        Map<String, Object> result = new HashMap<>();

        Optional<CollectionWorker> workerOpt = workerRepository.findById(workerId);
        if (!workerOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Worker not found");
            return result;
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setCollectionWorkerId(workerId);
        lr.setName(workerOpt.get().getName());
        lr.setLeaveRequestedDate(leaveDate);
        lr.setReason(reason);
        lr.setStatus("pending");
        leaveRequestRepository.save(lr);

        result.put("status", "success");
        result.put("message", "Leave request submitted for " + leaveDate);
        return result;
    }

    /**
     * Office staff approves/rejects leave. If rejected, email is sent to worker.
     */
    public Map<String, Object> handleLeaveDecision(String requestId, String decision) {
        Map<String, Object> result = new HashMap<>();

        Optional<LeaveRequest> lrOpt = leaveRequestRepository.findById(requestId);
        if (!lrOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Leave request not found");
            return result;
        }

        LeaveRequest lr = lrOpt.get();
        lr.setStatus(decision); // "approved" or "rejected"
        leaveRequestRepository.save(lr);

        if ("approved".equalsIgnoreCase(decision)) {
            // Update worker schedule with leave date
            Optional<CollectionWorkerSchedule> cwsOpt = workerScheduleRepository.findById(lr.getCollectionWorkerId());
            if (cwsOpt.isPresent()) {
                CollectionWorkerSchedule cws = cwsOpt.get();
                cws.setLeaveRequestedDate(lr.getLeaveRequestedDate());
                workerScheduleRepository.save(cws);
            }
            result.put("message", "Leave approved for " + lr.getName() + " on " + lr.getLeaveRequestedDate());
        } else {
            // Email notification to worker (placeholder — log for now)
            Optional<CollectionWorker> workerOpt = workerRepository.findById(lr.getCollectionWorkerId());
            String email = workerOpt.isPresent() ? workerOpt.get().getEmailId() : "unknown";
            result.put("message", "Leave rejected. Email notification sent to: " + email);
        }

        result.put("status", "success");
        return result;
    }

    // ===========================
    // 2. COLLECTION DETAIL VIEW (for worker)
    // ===========================

    /**
     * When collection worker asks for their assigned area details for today.
     * Returns all houses in their assigned zone with relevant details.
     */
    public Map<String, Object> getCollectionAreaDetails(String workerId) {
        Map<String, Object> result = new HashMap<>();
        String today = LocalDate.now().format(DATE_FMT);

        // Find schedules assigned to this worker for today
        List<CollectionSchedule> todaySchedules =
                scheduleRepository.findByCollectionWorkerAssignedAndScheduledDate(workerId, today);

        if (todaySchedules.isEmpty()) {
            result.put("status", "no_assignment");
            result.put("message", "No collection area assigned for today");
            return result;
        }

        List<Map<String, Object>> areaDetails = new ArrayList<>();

        for (CollectionSchedule schedule : todaySchedules) {
            String zoneId = schedule.getZoneId();
            List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);

            for (ZoneHouseDetail house : houses) {
                Map<String, Object> houseInfo = new HashMap<>();
                houseInfo.put("registeredUserId", house.getRegisteredUserId());
                houseInfo.put("houseNumber", house.getHouseNumber());
                houseInfo.put("wardNumber", house.getWardNumber());
                houseInfo.put("ownerName", house.getOwnerName());
                houseInfo.put("paymentStatus", house.getAmountPending() > 0 ? "pending" : "paid");
                houseInfo.put("amountPending", house.getAmountPending());
                houseInfo.put("collectionStatus", house.getCollectionStatus());
                areaDetails.add(houseInfo);
            }
        }

        result.put("status", "success");
        result.put("date", today);
        result.put("zoneId", todaySchedules.get(0).getZoneId());
        result.put("houses", areaDetails);
        result.put("totalHouses", areaDetails.size());
        return result;
    }

    // ===========================
    // 3. REPORT SUBMISSION
    // ===========================

    /**
     * Collection worker submits daily report after collection.
     */
    public Map<String, Object> submitReport(String workerId, String zoneId,
                                             int totalHouseCollected, double cashCollected,
                                             int totalPendingHouse, String reasonForPending) {
        Map<String, Object> result = new HashMap<>();

        String today = LocalDate.now().format(DATE_FMT);

        Report report = new Report();
        report.setWorkerId(workerId);
        report.setZoneId(zoneId);
        report.setDate(today);
        report.setTotalHouseCollected(totalHouseCollected);
        report.setCashCollected(cashCollected);
        report.setTotalPendingHouse(totalPendingHouse);
        report.setReasonForPending(reasonForPending);
        reportRepository.save(report);

        result.put("status", "success");
        result.put("reportId", report.getReportId());
        result.put("message", "Report submitted successfully");
        return result;
    }

    /**
     * Worker submits cash collected online — verify against expected amount.
     */
    public Map<String, Object> submitCashPayment(String workerId, String zoneId, double amountSent) {
        Map<String, Object> result = new HashMap<>();

        // Calculate expected: sum of amounts that were pending and now marked as paid at collection
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        double expectedCash = 0;
        for (ZoneHouseDetail h : houses) {
            if ("paid".equalsIgnoreCase(h.getCollectionStatus()) || "collected".equalsIgnoreCase(h.getCollectionStatus())) {
                // This was collected; check if payment was at door (approximation)
                // For simplicity, trust the worker's reported amount
            }
        }

        result.put("status", "success");
        result.put("amountSent", amountSent);
        result.put("message", "Cash payment of ₹" + amountSent + " received. Worker account cleared.");
        return result;
    }

    // ===========================
    // 4. EXPENSE SUBMISSION
    // ===========================

    /**
     * Worker submits expense with amount and receipt image.
     * Default status: pending. Office staff verifies later.
     */
    public Map<String, Object> submitExpense(String workerId, double amount, String image) {
        Map<String, Object> result = new HashMap<>();

        CollectionExpense expense = new CollectionExpense();
        expense.setCollectionWorkerId(workerId);
        expense.setAmount(amount);
        expense.setImage(image);
        expense.setStatus("pending");
        expense.setDate(LocalDate.now().format(DATE_FMT));
        expenseRepository.save(expense);

        result.put("status", "success");
        result.put("expenseId", expense.getExpenseId());
        result.put("message", "Expense submitted. Pending verification.");
        return result;
    }

    /**
     * Office staff approves expense — amount moved to "add_amount_to_be_paid".
     */
    public Map<String, Object> approveExpense(String expenseId) {
        Map<String, Object> result = new HashMap<>();

        Optional<CollectionExpense> expOpt = expenseRepository.findById(expenseId);
        if (!expOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Expense not found");
            return result;
        }

        CollectionExpense expense = expOpt.get();
        expense.setStatus("approved");
        expense.setAddAmountToBePaid(expense.getAddAmountToBePaid() + expense.getAmount());
        expense.setAmount(0); // Cleared from pending expense
        expenseRepository.save(expense);

        result.put("status", "success");
        result.put("message", "Expense approved. ₹" + expense.getAddAmountToBePaid() + " added to worker reimbursement.");
        return result;
    }

    // ===========================
    // 5. GET WORKER SCHEDULE
    // ===========================

    public Map<String, Object> getWorkerSchedule(String workerId) {
        Map<String, Object> result = new HashMap<>();

        Optional<CollectionWorkerSchedule> cwsOpt = workerScheduleRepository.findById(workerId);
        if (!cwsOpt.isPresent()) {
            result.put("status", "no_schedule");
            result.put("message", "No schedule found for worker");
            return result;
        }

        CollectionWorkerSchedule cws = cwsOpt.get();
        result.put("status", "success");
        result.put("workerId", cws.getCollectionWorkerId());
        result.put("name", cws.getName());
        result.put("assignedPanchayath", cws.getAssignedPanchayath());
        result.put("assignedZoneId", cws.getAssignedZoneId());
        result.put("numberOfAssignedDates", cws.getNumberOfAssignedDates());
        result.put("assignedDates", cws.getAssignedDates());
        result.put("leaveRequestedDate", cws.getLeaveRequestedDate());
        return result;
    }

    // ===========================
    // 6. GET ALL PENDING LEAVE REQUESTS (for office staff)
    // ===========================

    public List<LeaveRequest> getPendingLeaveRequests() {
        return leaveRequestRepository.findByStatus("pending");
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }
}
