package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Optional;

@Service
public class ZoningService {

    @Autowired
    private ZoneKottayamRepository kottayamRepo;

    @Autowired
    private ZoneErnakulamRepository ernakulamRepo;

    public String assignUserToZone(RegisteredUser newUser) {
        String distId = newUser.getDistId();
        String localBody = newUser.getPanchayathOrMunicipalityName() != null
                ? newUser.getPanchayathOrMunicipalityName().trim()
                : "";
        int ward = newUser.getWardNo();

        if ("Dist-05".equalsIgnoreCase(distId) || "Kottayam".equalsIgnoreCase(distId)) {
            return processKottayamZoning(localBody, ward);
        } else if ("Dist-06".equalsIgnoreCase(distId) || "Ernakulam".equalsIgnoreCase(distId)) {
            return processErnakulamZoning(localBody, ward);
        }
        return "UNZONED";
    }

    private String processKottayamZoning(String localBody, int ward) {
        // Step 1: Query database directly - Is this ward already assigned in an
        // existing zone for this local body?
        if (kottayamRepo.existsByLocalBodyNameAndWardsIn(localBody, ward)) {
            return kottayamRepo.findAll().stream()
                    .filter(z -> z.getLocalBodyName().equalsIgnoreCase(localBody) && z.getWards().contains(ward))
                    .findFirst()
                    .map(ZoneKottayam::getZoneId)
                    .orElse("ZONE_KOT_01");
        }

        // Step 2: Query database directly - Is there an existing zone for this local
        // body with status "OPEN"?
        Optional<ZoneKottayam> openZoneOpt = kottayamRepo.findByLocalBodyNameAndStatus(localBody, "OPEN");
        if (openZoneOpt.isPresent()) {
            ZoneKottayam zone = openZoneOpt.get();
            if (zone.getWards() == null) {
                zone.setWards(new ArrayList<>());
            }
            zone.getWards().add(ward);
            zone.setStatus("CLOSED"); // Reached its capacity of 2 concurrent wards
            kottayamRepo.save(zone);
            return zone.getZoneId();
        }

        // Step 3: No matching open zone found. Create a clean new zone document
        // sequence.
        long nextIndex = kottayamRepo.count() + 1;
        String nextId = "ZONE_KOT_" + String.format("%02d", nextIndex);

        ZoneKottayam newZone = new ZoneKottayam(nextId, localBody, ward);
        newZone.setStatus("OPEN"); // Set to OPEN so a second concurrent ward can join later
        kottayamRepo.save(newZone);

        return nextId;
    }

    private String processErnakulamZoning(String localBody, int ward) {
        // Step 1: Query database directly - Is this ward already assigned?
        if (ernakulamRepo.existsByLocalBodyNameAndWardsIn(localBody, ward)) {
            return ernakulamRepo.findAll().stream()
                    .filter(z -> z.getLocalBodyName().equalsIgnoreCase(localBody) && z.getWards().contains(ward))
                    .findFirst()
                    .map(ZoneErnakulam::getZoneId)
                    .orElse("ZONE_ERN_01");
        }

        // Step 2: Query database directly - Is there an existing zone with an available
        // open slot?
        Optional<ZoneErnakulam> openZoneOpt = ernakulamRepo.findByLocalBodyNameAndStatus(localBody, "OPEN");
        if (openZoneOpt.isPresent()) {
            ZoneErnakulam zone = openZoneOpt.get();
            if (zone.getWards() == null) {
                zone.setWards(new ArrayList<>());
            }
            zone.getWards().add(ward);
            zone.setStatus("CLOSED"); // Filled concurrent pair capacity
            ernakulamRepo.save(zone);
            return zone.getZoneId();
        }

        // Step 3: Create a clean new zone tracker
        long nextIndex = ernakulamRepo.count() + 1;
        String nextId = "ZONE_ERN_" + String.format("%02d", nextIndex);

        ZoneErnakulam newZone = new ZoneErnakulam(nextId, localBody, ward);
        newZone.setStatus("OPEN");
        ernakulamRepo.save(newZone);

        return nextId;
    }
}