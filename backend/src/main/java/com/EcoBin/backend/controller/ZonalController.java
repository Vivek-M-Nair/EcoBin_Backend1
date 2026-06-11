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

    @Autowired
    private ZoneHouseDetailRepository zoneHouseDetailRepository;

    @GetMapping("/{zoneId}/houses")
    public ResponseEntity<List<Map<String, Object>>> getHousesByZone(@PathVariable String zoneId) {
        System.out.println("🛰️ ECOBIN BACKEND: Fetching houses for zone: " + zoneId);
        List<ZoneHouseDetail> houses = zoneHouseDetailRepository.findByZoneId(zoneId);
        List<Map<String, Object>> responseList = new ArrayList<>();
        
        for (ZoneHouseDetail house : houses) {
            Map<String, Object> map = new HashMap<>();
            map.put("registeredUserId", house.getRegisteredUserId());
            map.put("zoneId", house.getZoneId());
            map.put("wardNumber", house.getWardNumber());
            map.put("houseNumber", house.getHouseNumber());
            map.put("ownerName", house.getOwnerName());
            map.put("collectionStatus", house.getCollectionStatus());
            map.put("amountPending", house.getAmountPending());
            map.put("otp", house.getOtp());
            
            // Look up address (panchayath/municipality name and ward number)
            map.put("houseNo", house.getHouseNumber());
            if (house.getRegisteredUserId() != null) {
                Optional<RegisteredUser> userOpt = registeredUserRepository.findById(house.getRegisteredUserId());
                if (userOpt.isPresent()) {
                    RegisteredUser user = userOpt.get();
                    String panchayath = user.getPanchayathOrMunicipalityName();
                    int ward = user.getWardNo();
                    map.put("address", (panchayath != null ? panchayath : "No Address") + ", Ward " + ward);
                    map.put("houseName", user.getHouseName() != null ? user.getHouseName() : "");
                } else {
                    map.put("address", "No Address");
                    map.put("houseName", "");
                }
            } else {
                map.put("address", "No Address");
                map.put("houseName", "");
            }
            responseList.add(map);
        }
        
        return ResponseEntity.ok(responseList);
    }

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