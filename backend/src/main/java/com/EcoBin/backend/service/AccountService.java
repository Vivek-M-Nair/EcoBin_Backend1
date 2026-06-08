package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Handles account deletion for registered users and collection workers.
 * Removes all related records from corresponding tables.
 */
@Service
public class AccountService {

    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private RegisteredUserPaymentRepository paymentRepository;
    @Autowired private RegisteredUserCollectionRepository collectionRepository;
    @Autowired private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired private CollectionWorkerRepository workerRepository;
    @Autowired private CollectionWorkerScheduleRepository workerScheduleRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private CollectionExpenseRepository expenseRepository;
    @Autowired private ImmediateCollectionReasonRepository immediateReasonRepository;

    /**
     * Delete a registered user account and all related data.
     */
    public Map<String, Object> deleteUserAccount(String userId) {
        Map<String, Object> result = new HashMap<>();

        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
        if (!userOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "User not found");
            return result;
        }

        // Delete from Registered_user
        registeredUserRepository.deleteById(userId);

        // Delete from registered_user_payment
        paymentRepository.deleteById(userId);

        // Delete from registered_user_collections
        collectionRepository.deleteById(userId);

        // Delete from Zone_house_detail
        zoneHouseDetailRepository.deleteById(userId);

        // Delete immediate collection reasons
        List<ImmediateCollectionReason> reasons = immediateReasonRepository.findByUserId(userId);
        immediateReasonRepository.deleteAll(reasons);

        result.put("status", "success");
        result.put("message", "User account and all related data deleted successfully.");
        return result;
    }

    /**
     * Delete a collection worker account and all related data.
     */
    public Map<String, Object> deleteWorkerAccount(String workerId) {
        Map<String, Object> result = new HashMap<>();

        Optional<CollectionWorker> workerOpt = workerRepository.findById(workerId);
        if (!workerOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Worker not found");
            return result;
        }

        // Delete from Collection_workers
        workerRepository.deleteById(workerId);

        // Delete from collection_worker_schedule
        workerScheduleRepository.deleteById(workerId);

        // Delete leave requests
        List<LeaveRequest> leaves = leaveRequestRepository.findByCollectionWorkerId(workerId);
        leaveRequestRepository.deleteAll(leaves);

        // Delete reports
        List<Report> reports = reportRepository.findByWorkerId(workerId);
        reportRepository.deleteAll(reports);

        // Delete expenses
        List<CollectionExpense> expenses = expenseRepository.findByCollectionWorkerId(workerId);
        expenseRepository.deleteAll(expenses);

        result.put("status", "success");
        result.put("message", "Worker account and all related data deleted successfully.");
        return result;
    }
}
