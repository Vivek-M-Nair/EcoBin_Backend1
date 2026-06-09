package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import com.EcoBin.backend.service.ZoningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller handling structural multi-ward zoning calculations for the
 * web portal.
 * Exposes endpoints to check for unassigned residents and update their tracking
 * layout profiles.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/zone")
public class ZonalController {

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private ZoningService zoningService;

    @GetMapping("/status")
    public String getZoningStatus() {
        return ZoningService.currentStatus;
    }

    /**
     * Scans for users marked as UNZONED, clusters them into localized multi-ward
     * parameters,
     * and sends clear feedback parameters back to the web portal.
     */
    @PostMapping("/check-update")
    public ResponseEntity<?> checkAndUpdateZones() {
        System.out.println("[ZONAL CONTROLLER] Initializing Zoning Process...");
        try {
            String statusResult = zoningService.performZoningProcess();
            if ("success".equalsIgnoreCase(statusResult)) {
                System.out.println("[ZONAL CONTROLLER] Zone Updation/Creation is Complete.");
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Zone Updation/Creation is Complete."));
            } else {
                System.out.println("[ZONAL CONTROLLER] Zone Updation/Creation was not needed.");
                return ResponseEntity.ok(Map.of(
                        "status", "no_change",
                        "message", "Zone Updation/Creation was not needed."));
            }
        } catch (Exception e) {
            System.err.println("[ZONAL CONTROLLER] ALERT: Zone Updation Operation Failure! Details: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "ALERT: Zone Updation Operation Failure! Details: " + e.getMessage()));
        }
    }
}