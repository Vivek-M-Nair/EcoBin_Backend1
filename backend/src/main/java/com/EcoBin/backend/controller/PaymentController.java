package com.EcoBin.backend.controller;

import com.EcoBin.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for payment processing and point redemption.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired private PaymentService paymentService;
    @Autowired private com.EcoBin.backend.repository.RegisteredUserPaymentRepository registeredUserPaymentRepository;

    @GetMapping("/all")
    public ResponseEntity<?> getAllPayments() {
        return ResponseEntity.ok(Map.of("status", "success", "payments", registeredUserPaymentRepository.findAll()));
    }

    // ===========================
    // 1. VIEW PENDING PAYMENT
    // ===========================

    @GetMapping("/pending/{userId}")
    public ResponseEntity<?> getPendingPayment(@PathVariable String userId) {
        Map<String, Object> result = paymentService.getPendingPayment(userId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 2. PROCESS PAYMENT
    // ===========================

    public static class PaymentDTO {
        public String userId;
        public double amountPaid;
    }

    @PostMapping("/pay")
    public ResponseEntity<?> processPayment(@RequestBody PaymentDTO request) {
        Map<String, Object> result = paymentService.processPayment(request.userId, request.amountPaid);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // 3. POINT REDEMPTION
    // ===========================

    public static class RedeemDTO {
        public String userId;
        public String redeemType; // "sponsor_tree", "cup", "payment_reduction"
    }

    @PostMapping("/redeem")
    public ResponseEntity<?> redeemPoints(@RequestBody RedeemDTO request) {
        Map<String, Object> result = paymentService.redeemPoints(request.userId, request.redeemType);
        return ResponseEntity.ok(result);
    }
}
