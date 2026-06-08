package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Handles payment processing, point calculation, and point redemption.
 */
@Service
public class PaymentService {

    @Autowired private RegisteredUserPaymentRepository paymentRepository;
    @Autowired private RegisteredUserCollectionRepository collectionRepository;
    @Autowired private ZoneHouseDetailRepository zoneHouseDetailRepository;
    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private CollectionScheduleRepository scheduleRepository;

    // ===========================
    // 1. PROCESS PAYMENT
    // ===========================

    /**
     * When user pays their pending amount.
     * Recalculates pending, updates history, calculates and updates points.
     */
    public Map<String, Object> processPayment(String userId, double amountPaid) {
        Map<String, Object> result = new HashMap<>();

        Optional<RegisteredUserPayment> payOpt = paymentRepository.findById(userId);
        if (!payOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Payment record not found for user: " + userId);
            return result;
        }

        RegisteredUserPayment payment = payOpt.get();

        // Recalculate pending
        double newPending = payment.getAmountPending() - amountPaid;
        if (newPending < 0) newPending = 0;
        payment.setAmountPending(newPending);

        // Update last 10 times payment history
        List<String> history = payment.getLast10TimesDetails();
        if (history == null) history = new ArrayList<>();
        history.add(0, "Paid_₹" + amountPaid);
        if (history.size() > 10) {
            history = new ArrayList<>(history.subList(0, 10));
        }
        payment.setLast10TimesDetails(history);

        // Calculate points: 10 points per ₹50 paid
        int pointsEarned = (int) (amountPaid / 5);
        payment.setPointEarned(payment.getPointEarned() + pointsEarned);
        payment.setPendingPoint(payment.getPendingPoint() + pointsEarned);

        paymentRepository.save(payment);

        // Also update zone house detail payment status
        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
        if (userOpt.isPresent()) {
            String zoneId = userOpt.get().getZoneId();
            List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
            for (ZoneHouseDetail h : houses) {
                if (userId.equals(h.getRegisteredUserId())) {
                    h.setAmountPending(newPending);
                    if (newPending == 0) {
                        h.setCollectionStatus("paid");
                    }
                    zoneHouseDetailRepository.save(h);
                    break;
                }
            }
        }

        result.put("status", "success");
        result.put("amountPaid", amountPaid);
        result.put("newPendingAmount", newPending);
        result.put("totalPointsEarned", payment.getPointEarned());
        result.put("availablePoints", payment.getPendingPoint());
        return result;
    }

    // ===========================
    // 2. GET PENDING PAYMENT
    // ===========================

    public Map<String, Object> getPendingPayment(String userId) {
        Map<String, Object> result = new HashMap<>();

        Optional<RegisteredUserPayment> payOpt = paymentRepository.findById(userId);
        if (!payOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "No payment record found");
            return result;
        }

        RegisteredUserPayment payment = payOpt.get();
        result.put("status", "success");
        result.put("amountPending", payment.getAmountPending());
        result.put("last10TimesDetails", payment.getLast10TimesDetails());
        result.put("totalPointsEarned", payment.getPointEarned());
        result.put("availablePoints", payment.getPendingPoint());
        return result;
    }

    // ===========================
    // 3. POINT REDEMPTION
    // ===========================

    /**
     * Redeem points for rewards:
     * - "sponsor_tree" → 500 points → tree planted message
     * - "cup" → 250 points → collect from office
     * - "payment_reduction" → 1000 points → next collection is free
     */
    public Map<String, Object> redeemPoints(String userId, String redeemType) {
        Map<String, Object> result = new HashMap<>();

        Optional<RegisteredUserPayment> payOpt = paymentRepository.findById(userId);
        if (!payOpt.isPresent()) {
            result.put("status", "error");
            result.put("message", "Payment record not found");
            return result;
        }

        RegisteredUserPayment payment = payOpt.get();
        int availablePoints = payment.getPendingPoint();

        switch (redeemType.toLowerCase()) {
            case "sponsor_tree":
                if (availablePoints < 500) {
                    result.put("status", "error");
                    result.put("message", "Not enough points. You have " + availablePoints + " points, need 500.");
                    return result;
                }
                payment.setPendingPoint(availablePoints - 500);
                paymentRepository.save(payment);
                result.put("status", "success");
                result.put("message", "Thank you! Your tree will be planted. 🌳 We appreciate your contribution to a greener planet!");
                result.put("pointsUsed", 500);
                result.put("remainingPoints", payment.getPendingPoint());
                break;

            case "cup":
                if (availablePoints < 250) {
                    result.put("status", "error");
                    result.put("message", "Not enough points. You have " + availablePoints + " points, need 250.");
                    return result;
                }
                payment.setPendingPoint(availablePoints - 250);
                paymentRepository.save(payment);
                result.put("status", "success");
                result.put("message", "Congratulations! You can collect your eco-friendly cup from our office near you. ☕");
                result.put("pointsUsed", 250);
                result.put("remainingPoints", payment.getPendingPoint());
                break;

            case "payment_reduction":
                if (availablePoints < 1000) {
                    result.put("status", "error");
                    result.put("message", "Not enough points. You have " + availablePoints + " points, need 1000.");
                    return result;
                }
                payment.setPendingPoint(availablePoints - 1000);
                paymentRepository.save(payment);

                // Mark next collection as paid for this user
                markNextCollectionAsPaid(userId);

                result.put("status", "success");
                result.put("message", "Your next waste collection is now FREE! Points redeemed successfully. 🎉");
                result.put("pointsUsed", 1000);
                result.put("remainingPoints", payment.getPendingPoint());
                break;

            default:
                result.put("status", "error");
                result.put("message", "Invalid redeem type. Choose: sponsor_tree, cup, or payment_reduction.");
                break;
        }

        return result;
    }

    /**
     * Mark next collection as paid (free) for user who redeemed payment_reduction.
     */
    private void markNextCollectionAsPaid(String userId) {
        // Find user's zone
        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(userId);
        if (userOpt.isPresent()) {
            String zoneId = userOpt.get().getZoneId();

            // Update zone house detail
            List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
            for (ZoneHouseDetail h : houses) {
                if (userId.equals(h.getRegisteredUserId())) {
                    h.setAmountPending(0);
                    h.setCollectionStatus("paid_via_points");
                    zoneHouseDetailRepository.save(h);
                    break;
                }
            }

            // Also zero out in payment record for next collection
            Optional<RegisteredUserPayment> payOpt = paymentRepository.findById(userId);
            if (payOpt.isPresent()) {
                RegisteredUserPayment pay = payOpt.get();
                pay.setAmountPending(0);
                paymentRepository.save(pay);
            }
        }
    }
}
