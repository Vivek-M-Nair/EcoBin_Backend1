package com.EcoBin.backend.controller;

import com.EcoBin.backend.service.SchedulingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller mapping structural administrative scheduling demands
 * dispatched from the Office Staff web portal.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/collection")
public class ScheduleController {

    @Autowired
    private SchedulingService schedulingService;

    /**
     * Endpoint triggered when the office staff clicks the main "Schedule" action
     * button.
     * Iterates through zones, checks the 15-day restriction rules against
     * Last_time_collection, and auto-generates upcoming collection dates.
     */
    @PostMapping("/auto-generate-all")
    public ResponseEntity<?> triggerAutomatedZoningSchedule() {
        try {
            schedulingService.scheduleAllZones();
            schedulingService.assignWorkersForUpcomingCollections();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message",
                    "Global optimization complete! All qualifying zones have been dynamically scheduled between 24-28 days from their last collection."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Scheduling pipeline failed: " + e.getMessage()));
        }
    }

    /**
     * Endpoint triggered when the office staff clicks "Assign_Collection_worker".
     * Scans for all upcoming scheduled collections and assigns workers based on local body matching,
     * leave requests, and backup rules.
     */
    @PostMapping("/assign-workers")
    public ResponseEntity<?> triggerWorkerAssignment() {
        try {
            schedulingService.assignWorkersForAllUpcoming();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Collection workers successfully assigned to all upcoming scheduled zones. Backup requirements satisfied."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Worker assignment failed: " + e.getMessage()));
        }
    }
}