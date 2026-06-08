package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ZoningService {

    @Autowired
    private ZoneKottayamRepository kottayamRepo;
    @Autowired
    private ZoneErnakulamRepository ernakulamRepo;

    public void assignUserToZone(HouseDetails newHouse) {
        String distId = newHouse.getDistId();
        String localBody = newHouse.getLocalBodyName().trim();
        int ward = newHouse.getWardNo();

        if ("Dist-05".equalsIgnoreCase(distId)) {
            processKottayamZoning(localBody, ward);
        } else if ("Dist-06".equalsIgnoreCase(distId)) {
            processErnakulamZoning(localBody, ward);
        }
    }

    private void processKottayamZoning(String localBody, int ward) {
        List<ZoneKottayam> zones = kottayamRepo.findAll();

        // Step A: If ward is already allocated within this local body, exit early
        for (ZoneKottayam zone : zones) {
            if (zone.getLocalBodyName().equalsIgnoreCase(localBody) && zone.getWards().contains(ward)) {
                return;
            }
        }

        // Step B: Look for an OPEN zone for this specific body that has a slot
        for (ZoneKottayam zone : zones) {
            if (zone.getLocalBodyName().equalsIgnoreCase(localBody) && "OPEN".equalsIgnoreCase(zone.getStatus())) {
                zone.getWards().add(ward);
                zone.setStatus("CLOSED"); // Reached maximum capacity (2 wards)
                kottayamRepo.save(zone);
                return;
            }
        }

        // Step C: No open slots or zones found. Spin up a new zone document sequence
        long nextIndex = kottayamRepo.count() + 1;
        String nextId = "ZONE_KOT_" + String.format("%02d", nextIndex);
        ZoneKottayam newZone = new ZoneKottayam(nextId, localBody, ward);
        kottayamRepo.save(newZone);
    }

    private void processErnakulamZoning(String localBody, int ward) {
        List<ZoneErnakulam> zones = ernakulamRepo.findAll();

        for (ZoneErnakulam zone : zones) {
            if (zone.getLocalBodyName().equalsIgnoreCase(localBody) && zone.getWards().contains(ward)) {
                return;
            }
        }

        for (ZoneErnakulam zone : zones) {
            if (zone.getLocalBodyName().equalsIgnoreCase(localBody) && "OPEN".equalsIgnoreCase(zone.getStatus())) {
                zone.getWards().add(ward);
                zone.setStatus("CLOSED");
                ernakulamRepo.save(zone);
                return;
            }
        }

        long nextIndex = ernakulamRepo.count() + 1;
        String nextId = "ZONE_ERN_" + String.format("%02d", nextIndex);
        ZoneErnakulam newZone = new ZoneErnakulam(nextId, localBody, ward);
        ernakulamRepo.save(newZone);
    }
}