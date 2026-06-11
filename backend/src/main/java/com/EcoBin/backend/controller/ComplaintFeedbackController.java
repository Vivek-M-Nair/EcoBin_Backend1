package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for complaints and feedback.
 * Complaints can be submitted without login (unregistered users allowed).
 * Feedback requires a description and star rating (1-5).
 *
 * Admin/office staff can view and manage complaints and feedback using passkey.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api")
public class ComplaintFeedbackController {

    private static final String OFFICE_STAFF_KEY = "office_pass";
    private static final String ADMIN_KEY = "adm_pass";

    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private FeedbackRepository feedbackRepository;

    // ===========================
    // COMPLAINTS
    // ===========================

    public static class ComplaintDTO {
        public String complaintDescription;
        public String image; // optional, Base64 or URL
    }

    /**
     * Submit complaint — no login required.
     */
    @PostMapping("/complaint/submit")
    public ResponseEntity<?> submitComplaint(@RequestBody ComplaintDTO request) {
        Complaint complaint = new Complaint();
        complaint.setComplaintDescription(request.complaintDescription);
        if (request.image != null && !request.image.trim().isEmpty()) {
            complaint.setImage(request.image);
        }
        complaint.setStatus("pending");
        complaintRepository.save(complaint);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "complaintId", complaint.getComplaintId(),
                "message", "Complaint submitted successfully. We will look into it."));
    }

    public static class WorkerComplaintDTO {
        public String workerId;
        public String houseId;
        public String category;
        public String description;
        public String photoUrl;
    }

    /**
     * Submit complaint by collection worker.
     */
    @PostMapping("/complaint")
    public ResponseEntity<?> submitWorkerComplaint(@RequestBody WorkerComplaintDTO request) {
        Complaint complaint = new Complaint();
        String fullDescription = String.format("[%s] House: %s (Reported by Worker: %s) - %s",
                request.category != null ? request.category : "N/A",
                request.houseId != null ? request.houseId : "N/A",
                request.workerId != null ? request.workerId : "N/A",
                request.description != null ? request.description : "");
        complaint.setComplaintDescription(fullDescription);
        if (request.photoUrl != null && !request.photoUrl.trim().isEmpty()) {
            complaint.setImage(request.photoUrl);
        }
        complaint.setStatus("pending");
        complaintRepository.save(complaint);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "complaintId", complaint.getComplaintId(),
                "message", "Complaint logged successfully"));
    }

    /**
     * View all complaints (office staff/admin only).
     */
    @GetMapping("/complaint/all")
    public ResponseEntity<?> getAllComplaints(@RequestParam String passkey) {
        if (!isAuthorized(passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized"));
        }
        List<Complaint> complaints = complaintRepository.findAll();
        return ResponseEntity.ok(Map.of("status", "success", "complaints", complaints));
    }

    /**
     * Update complaint status (office staff/admin).
     */
    public static class ComplaintUpdateDTO {
        public String complaintId;
        public String status; // "solved" or "pending"
        public String passkey;
    }

    @PostMapping("/complaint/update")
    public ResponseEntity<?> updateComplaintStatus(@RequestBody ComplaintUpdateDTO request) {
        if (!isAuthorized(request.passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized"));
        }

        Optional<Complaint> compOpt = complaintRepository.findById(request.complaintId);
        if (!compOpt.isPresent()) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Complaint not found"));
        }

        Complaint complaint = compOpt.get();
        complaint.setStatus(request.status);
        complaintRepository.save(complaint);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Complaint status updated to: " + request.status));
    }

    /**
     * Delete a complaint (office staff/admin).
     */
    @DeleteMapping("/complaint/delete/{complaintId}")
    public ResponseEntity<?> deleteComplaint(@PathVariable String complaintId, @RequestParam String passkey) {
        if (!isAuthorized(passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized"));
        }

        if (!complaintRepository.existsById(complaintId)) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Complaint not found"));
        }

        complaintRepository.deleteById(complaintId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Complaint deleted."));
    }

    // ===========================
    // FEEDBACK
    // ===========================

    public static class FeedbackDTO {
        public String feedbackDescription; // optional
        public int rating; // 1 to 5
    }

    /**
     * Submit feedback with star rating.
     */
    @PostMapping("/feedback/submit")
    public ResponseEntity<?> submitFeedback(@RequestBody FeedbackDTO request) {
        if (request.rating < 1 || request.rating > 5) {
            return ResponseEntity.ok(Map.of("status", "error", "message", "Rating must be between 1 and 5."));
        }

        Feedback feedback = new Feedback();
        feedback.setFeedbackDescription(request.feedbackDescription);
        feedback.setRating(request.rating);
        feedbackRepository.save(feedback);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "feedbackId", feedback.getFeedbackId(),
                "message", "Thank you for your feedback!"));
    }

    /**
     * View all feedback (office staff/admin only).
     */
    @GetMapping("/feedback/all")
    public ResponseEntity<?> getAllFeedback(@RequestParam String passkey) {
        if (!isAuthorized(passkey)) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Unauthorized"));
        }
        List<Feedback> feedbackList = feedbackRepository.findAll();
        return ResponseEntity.ok(Map.of("status", "success", "feedback", feedbackList));
    }

    // ===========================
    // AUTHORIZATION HELPER
    // ===========================

    private boolean isAuthorized(String passkey) {
        return OFFICE_STAFF_KEY.equals(passkey) || ADMIN_KEY.equals(passkey);
    }
}
