package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.service.WorkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for collection worker operations:
 * leave requests, collection area detail, reports, expenses, schedule view.
 *
 * Role-key based access: passkey "office_pass" required for office-staff-only endpoints.
 */
@RestController
@RequestMapping("/api/worker")
public class WorkerController {

    private static final String OFFICE_STAFF_KEY = "office_pass";
    private static final String ADMIN_KEY = "adm_pass";

    @Autowired private WorkerService workerService;

    // ===========================
    // 1. LEAVE REQUEST (by worker)
    // ===========================

    public static class LeaveRequestDTO {
        public String workerId;
        public String leaveDate;
        public String reason;
    }

    @PostMapping("/leave/request")
    public ResponseEntity<?> submitLeaveRequest(@RequestBody LeaveRequestDTO request) {
        Map<String, Object> result = workerService.submitLeaveRequest(
                request.workerId, request.leaveDate, request.reason);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 2. LEAVE DECISION (by office staff)
    // ===========================

    public static class LeaveDecisionDTO {
        public String requestId;
        public String decision; // "approved" or "rejected"
        public String passkey;
    }

    @PostMapping("/leave/decision")
    public ResponseEntity<?> handleLeaveDecision(@RequestBody LeaveDecisionDTO request) {
        if (!isAuthorized(request.passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized. Invalid passkey."));
        }
        Map<String, Object> result = workerService.handleLeaveDecision(request.requestId, request.decision);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 3. COLLECTION AREA DETAIL (by worker)
    // ===========================

    @GetMapping("/collection-area/{workerId}")
    public ResponseEntity<?> getCollectionAreaDetails(@PathVariable String workerId) {
        Map<String, Object> result = workerService.getCollectionAreaDetails(workerId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 4. SUBMIT REPORT (by worker)
    // ===========================

    public static class ReportDTO {
        public String workerId;
        public String zoneId;
        public int totalHouseCollected;
        public double cashCollected;
        public int totalPendingHouse;
        public String reasonForPending;
    }

    @PostMapping("/report")
    public ResponseEntity<?> submitReport(@RequestBody ReportDTO request) {
        Map<String, Object> result = workerService.submitReport(
                request.workerId, request.zoneId, request.totalHouseCollected,
                request.cashCollected, request.totalPendingHouse, request.reasonForPending);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 5. SUBMIT CASH PAYMENT (by worker)
    // ===========================

    public static class CashPaymentDTO {
        public String workerId;
        public String zoneId;
        public double amountSent;
    }

    @PostMapping("/cash-payment")
    public ResponseEntity<?> submitCashPayment(@RequestBody CashPaymentDTO request) {
        Map<String, Object> result = workerService.submitCashPayment(
                request.workerId, request.zoneId, request.amountSent);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 6. SUBMIT EXPENSE (by worker)
    // ===========================

    public static class ExpenseDTO {
        public String workerId;
        public double amount;
        public String image; // Base64 receipt
    }

    @PostMapping("/expense")
    public ResponseEntity<?> submitExpense(@RequestBody ExpenseDTO request) {
        Map<String, Object> result = workerService.submitExpense(
                request.workerId, request.amount, request.image);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 7. APPROVE EXPENSE (by office staff)
    // ===========================

    public static class ExpenseApprovalDTO {
        public String expenseId;
        public String passkey;
    }

    @PostMapping("/expense/approve")
    public ResponseEntity<?> approveExpense(@RequestBody ExpenseApprovalDTO request) {
        if (!isAuthorized(request.passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized. Invalid passkey."));
        }
        Map<String, Object> result = workerService.approveExpense(request.expenseId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 8. VIEW WORKER SCHEDULE (by worker or office staff)
    // ===========================

    @GetMapping("/schedule/{workerId}")
    public ResponseEntity<?> getWorkerSchedule(@PathVariable String workerId) {
        Map<String, Object> result = workerService.getWorkerSchedule(workerId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 9. VIEW PENDING LEAVE REQUESTS (office staff only)
    // ===========================

    @GetMapping("/leave/pending")
    public ResponseEntity<?> getPendingLeaveRequests(@RequestParam String passkey) {
        if (!isAuthorized(passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized. Invalid passkey."));
        }
        List<LeaveRequest> requests = workerService.getPendingLeaveRequests();
        return ResponseEntity.ok(Map.of("status", "success", "leaveRequests", requests));
    }

    // ===========================
    // AUTHORIZATION HELPER
    // ===========================

    private boolean isAuthorized(String passkey) {
        return OFFICE_STAFF_KEY.equals(passkey) || ADMIN_KEY.equals(passkey);
    }
}
