package com.EcoBin.backend.service;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ZoningService {

    public static final ReentrantReadWriteLock zoningLock = new ReentrantReadWriteLock();
    public static volatile String currentStatus = "Idle";

    @Autowired
    private ZoneKottayamRepository kottayamRepo;

    @Autowired
    private ZoneErnakulamRepository ernakulamRepo;

    @Autowired
    private StateDistRepository stateDistRepository;

    @Autowired
    private ZoneHouseDetailRepository zoneHouseDetailRepository;

    @Autowired
    private RegisteredUserCollectionRepository registeredUserCollectionRepository;

    @Autowired
    private RegisteredUserPaymentRepository registeredUserPaymentRepository;

    @Autowired
    private LastTimeCollectionRepository lastTimeCollectionRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    /**
     * Assigns a single user to a zone (called during registration completion).
     */
    public String assignUserToZone(RegisteredUser newUser) {
        zoningLock.writeLock().lock();
        try {
            System.out.println("[ZONING ENGINE] Assigning single user to zone: " + newUser.getUserName());
            
            // Extract Fields
            String distId = newUser.getDistId();
            String localBody = newUser.getPanchayathOrMunicipalityName() != null ? newUser.getPanchayathOrMunicipalityName().trim() : "";
            int ward = newUser.getWardNo();

            // Determine target table
            String distName = "";
            if (distId != null) {
                Optional<StateDist> sdOpt = stateDistRepository.findById(distId);
                if (sdOpt.isPresent()) {
                    distName = sdOpt.get().getDistName();
                } else {
                    distName = distId;
                }
            }

            String zoneId = null;

            if ("Kottayam".equalsIgnoreCase(distName)) {
                // Kottayam Routing
                // Duplicate Check
                Optional<ZoneKottayam> dupOpt = kottayamRepo.findAll().stream()
                    .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                 && z.getWards() != null && z.getWards().contains(ward))
                    .findFirst();
                if (dupOpt.isPresent()) {
                    zoneId = dupOpt.get().getZoneId();
                } else {
                    // Check for Space
                    Optional<ZoneKottayam> spaceOpt = kottayamRepo.findAll().stream()
                        .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                     && z.getWards() != null && z.getWards().size() < 2)
                        .findFirst();
                    if (spaceOpt.isPresent()) {
                        ZoneKottayam zone = spaceOpt.get();
                        if (zone.getWards() == null) {
                            zone.setWards(new ArrayList<>());
                        }
                        zone.getWards().add(ward);
                        if (zone.getWards().size() == 2) {
                            zone.setStatus("CLOSED");
                        }
                        kottayamRepo.save(zone);
                        zoneId = zone.getZoneId();
                    } else {
                        // Create New Zone KTYM_XXX
                        List<ZoneKottayam> allK = kottayamRepo.findAll();
                        int maxNum = 0;
                        for (ZoneKottayam z : allK) {
                            String id = z.getZoneId();
                            if (id != null && id.startsWith("KTYM_")) {
                                try {
                                    int num = Integer.parseInt(id.substring(5));
                                    if (num > maxNum) maxNum = num;
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                        }
                        String newId = "KTYM_" + String.format("%03d", maxNum + 1);
                        ZoneKottayam newZone = new ZoneKottayam(newId, localBody, ward);
                        newZone.setStatus("OPEN");
                        kottayamRepo.save(newZone);
                        zoneId = newId;

                        // Initialize LastTimeCollection
                        if (!lastTimeCollectionRepository.existsById(newId)) {
                            LastTimeCollection ltc = new LastTimeCollection();
                            ltc.setZoneId(newId);
                            ltc.setLastCollectedDate(java.time.LocalDate.now().minusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                            ltc.setPrevious10Dates(new ArrayList<>());
                            lastTimeCollectionRepository.save(ltc);
                        }
                    }
                }
            } else if ("Ernakulam".equalsIgnoreCase(distName)) {
                // Ernakulam Routing
                // Duplicate Check
                Optional<ZoneErnakulam> dupOpt = ernakulamRepo.findAll().stream()
                    .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                 && z.getWards() != null && z.getWards().contains(ward))
                    .findFirst();
                if (dupOpt.isPresent()) {
                    zoneId = dupOpt.get().getZoneId();
                } else {
                    // Check for Space
                    Optional<ZoneErnakulam> spaceOpt = ernakulamRepo.findAll().stream()
                        .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                     && z.getWards() != null && z.getWards().size() < 2)
                        .findFirst();
                    if (spaceOpt.isPresent()) {
                        ZoneErnakulam zone = spaceOpt.get();
                        if (zone.getWards() == null) {
                            zone.setWards(new ArrayList<>());
                        }
                        zone.getWards().add(ward);
                        if (zone.getWards().size() == 2) {
                            zone.setStatus("CLOSED");
                        }
                        ernakulamRepo.save(zone);
                        zoneId = zone.getZoneId();
                    } else {
                        // Create New Zone EKM_XXX
                        List<ZoneErnakulam> allE = ernakulamRepo.findAll();
                        int maxNum = 0;
                        for (ZoneErnakulam z : allE) {
                            String id = z.getZoneId();
                            if (id != null && id.startsWith("EKM_")) {
                                try {
                                    int num = Integer.parseInt(id.substring(4));
                                    if (num > maxNum) maxNum = num;
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                        }
                        String newId = "EKM_" + String.format("%03d", maxNum + 1);
                        ZoneErnakulam newZone = new ZoneErnakulam(newId, localBody, ward);
                        newZone.setStatus("OPEN");
                        ernakulamRepo.save(newZone);
                        zoneId = newId;

                        // Initialize LastTimeCollection
                        if (!lastTimeCollectionRepository.existsById(newId)) {
                            LastTimeCollection ltc = new LastTimeCollection();
                            ltc.setZoneId(newId);
                            ltc.setLastCollectedDate(java.time.LocalDate.now().minusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                            ltc.setPrevious10Dates(new ArrayList<>());
                            lastTimeCollectionRepository.save(ltc);
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown or unsupported district: " + distName);
            }

            // Update registered_user table Zone_id
            newUser.setZoneId(zoneId);

            // Initialize user records if missing
            initializeUserRecords(newUser);

            // Update/Insert ZoneHouseDetail Table
            String userId = newUser.getHouseId();
            Optional<ZoneHouseDetail> detailOpt = zoneHouseDetailRepository.findById(userId);
            if (!detailOpt.isPresent()) {
                ZoneHouseDetail newDetail = new ZoneHouseDetail();
                newDetail.setRegisteredUserId(userId);
                newDetail.setZoneId(zoneId);
                newDetail.setWardNumber(ward);
                newDetail.setHouseNumber(newUser.getHouseNumber());
                newDetail.setOwnerName(newUser.getUserName());
                newDetail.setLastCollectedDate(new ArrayList<>());
                newDetail.setNextCollectedDate("Not scheduled");
                newDetail.setCollectionStatus("pending");
                newDetail.setAmountPending(0.0);
                newDetail.setOtp(null);
                zoneHouseDetailRepository.save(newDetail);
            } else {
                ZoneHouseDetail detail = detailOpt.get();
                detail.setZoneId(zoneId);
                detail.setWardNumber(ward);
                zoneHouseDetailRepository.save(detail);
            }

            return zoneId;
        } finally {
            zoningLock.writeLock().unlock();
        }
    }

    /**
     * Scans all users, runs the zoning clustering pipeline, updates the database,
     * and performs complete database state rollback if any exception occurs.
     */
    public String performZoningProcess() throws Exception {
        zoningLock.writeLock().lock();
        currentStatus = "Initializing Zoning Process...";
        System.out.println("[ZONING ENGINE] Starting batch zoning process...");

        // Backup states for rollback
        List<RegisteredUser> originalUsers = registeredUserRepository.findAll();
        List<ZoneHouseDetail> originalHouseDetails = zoneHouseDetailRepository.findAll();
        List<ZoneKottayam> originalKottayamZones = kottayamRepo.findAll();
        List<ZoneErnakulam> originalErnakulamZones = ernakulamRepo.findAll();
        
        // Clone backups to prevent in-memory mutability references
        List<RegisteredUser> backupUsers = new ArrayList<>();
        for (RegisteredUser u : originalUsers) {
            RegisteredUser bu = new RegisteredUser();
            bu.setHouseId(u.getHouseId());
            bu.setHouseNumber(u.getHouseNumber());
            bu.setHouseName(u.getHouseName());
            bu.setSubNo(u.getSubNo());
            bu.setDistId(u.getDistId());
            bu.setPanchayathOrMunicipalityName(u.getPanchayathOrMunicipalityName());
            bu.setWardNo(u.getWardNo());
            bu.setUserName(u.getUserName());
            bu.setPassword(u.getPassword());
            bu.setPhoneNumber(u.getPhoneNumber());
            bu.setEmailId(u.getEmailId());
            bu.setZoneId(u.getZoneId());
            bu.setPendingPayment(u.getPendingPayment());
            bu.setPoints(u.getPoints());
            backupUsers.add(bu);
        }

        List<ZoneHouseDetail> backupHouseDetails = new ArrayList<>();
        for (ZoneHouseDetail h : originalHouseDetails) {
            ZoneHouseDetail bh = new ZoneHouseDetail(
                h.getRegisteredUserId(),
                h.getZoneId(),
                h.getWardNumber(),
                h.getHouseNumber(),
                h.getOwnerName(),
                h.getLastCollectedDate() != null ? new ArrayList<>(h.getLastCollectedDate()) : new ArrayList<>(),
                h.getNextCollectedDate(),
                h.getCollectionStatus(),
                h.getAmountPending(),
                h.getOtp()
            );
            backupHouseDetails.add(bh);
        }

        List<ZoneKottayam> backupKottayam = new ArrayList<>();
        for (ZoneKottayam z : originalKottayamZones) {
            ZoneKottayam bz = new ZoneKottayam();
            bz.setZoneId(z.getZoneId());
            bz.setLocalBodyName(z.getLocalBodyName());
            bz.setWards(new ArrayList<>(z.getWards()));
            bz.setStatus(z.getStatus());
            backupKottayam.add(bz);
        }

        List<ZoneErnakulam> backupErnakulam = new ArrayList<>();
        for (ZoneErnakulam z : originalErnakulamZones) {
            ZoneErnakulam bz = new ZoneErnakulam();
            bz.setZoneId(z.getZoneId());
            bz.setLocalBodyName(z.getLocalBodyName());
            bz.setWards(new ArrayList<>(z.getWards()));
            bz.setStatus(z.getStatus());
            backupErnakulam.add(bz);
        }

        List<String> newLastTimeCollectionsCreated = new ArrayList<>();
        List<String> newCollectionsCreated = new ArrayList<>();
        List<String> newPaymentsCreated = new ArrayList<>();

        boolean anyChange = false;

        try {
            // Process users one by one
            for (RegisteredUser user : originalUsers) {
                // Initialize default user records if missing
                String userId = user.getHouseId();
                if (!registeredUserCollectionRepository.existsById(userId)) {
                    RegisteredUserCollection ruc = new RegisteredUserCollection(userId, "Not scheduled", new ArrayList<>(), "none");
                    registeredUserCollectionRepository.save(ruc);
                    newCollectionsCreated.add(userId);
                    anyChange = true;
                }
                if (!registeredUserPaymentRepository.existsById(userId)) {
                    RegisteredUserPayment rup = new RegisteredUserPayment(userId, 0.0, new ArrayList<>(), 0, 0);
                    registeredUserPaymentRepository.save(rup);
                    newPaymentsCreated.add(userId);
                    anyChange = true;
                }

                // Extract Fields
                String distId = user.getDistId();
                String localBody = user.getPanchayathOrMunicipalityName() != null ? user.getPanchayathOrMunicipalityName().trim() : "";
                int ward = user.getWardNo();

                // Determine target table
                String distName = "";
                if (distId != null) {
                    Optional<StateDist> sdOpt = stateDistRepository.findById(distId);
                    if (sdOpt.isPresent()) {
                        distName = sdOpt.get().getDistName();
                    } else {
                        distName = distId;
                    }
                }

                String zoneId = null;

                if ("Kottayam".equalsIgnoreCase(distName)) {
                    if (!"Processing Kottayam records...".equals(currentStatus)) {
                        currentStatus = "Processing Kottayam records...";
                    }
                    System.out.println("[ZONING ENGINE] Processing Kottayam record for user: " + user.getUserName());
                    // Kottayam Routing
                    // Duplicate Check
                    Optional<ZoneKottayam> dupOpt = kottayamRepo.findAll().stream()
                        .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                     && z.getWards() != null && z.getWards().contains(ward))
                        .findFirst();
                    if (dupOpt.isPresent()) {
                        zoneId = dupOpt.get().getZoneId();
                    } else {
                        // Check for Space
                        Optional<ZoneKottayam> spaceOpt = kottayamRepo.findAll().stream()
                            .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                         && z.getWards() != null && z.getWards().size() < 2)
                            .findFirst();
                        if (spaceOpt.isPresent()) {
                            ZoneKottayam zone = spaceOpt.get();
                            if (zone.getWards() == null) {
                                zone.setWards(new ArrayList<>());
                            }
                            zone.getWards().add(ward);
                            if (zone.getWards().size() == 2) {
                                zone.setStatus("CLOSED");
                            }
                            kottayamRepo.save(zone);
                            zoneId = zone.getZoneId();
                            anyChange = true;
                        } else {
                            // Create New Zone KTYM_XXX
                            List<ZoneKottayam> allK = kottayamRepo.findAll();
                            int maxNum = 0;
                            for (ZoneKottayam z : allK) {
                                String id = z.getZoneId();
                                if (id != null && id.startsWith("KTYM_")) {
                                    try {
                                        int num = Integer.parseInt(id.substring(5));
                                        if (num > maxNum) maxNum = num;
                                    } catch (NumberFormatException e) {
                                        // ignore
                                    }
                                }
                            }
                            String newId = "KTYM_" + String.format("%03d", maxNum + 1);
                            ZoneKottayam newZone = new ZoneKottayam(newId, localBody, ward);
                            newZone.setStatus("OPEN");
                            kottayamRepo.save(newZone);
                            zoneId = newId;

                            // Initialize LastTimeCollection
                            if (!lastTimeCollectionRepository.existsById(newId)) {
                                LastTimeCollection ltc = new LastTimeCollection();
                                ltc.setZoneId(newId);
                                ltc.setLastCollectedDate(java.time.LocalDate.now().minusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                                ltc.setPrevious10Dates(new ArrayList<>());
                                lastTimeCollectionRepository.save(ltc);
                                newLastTimeCollectionsCreated.add(newId);
                            }
                            anyChange = true;
                        }
                    }
                } else if ("Ernakulam".equalsIgnoreCase(distName)) {
                    if (!"Processing Ernakulam records...".equals(currentStatus)) {
                        currentStatus = "Processing Ernakulam records...";
                    }
                    System.out.println("[ZONING ENGINE] Processing Ernakulam record for user: " + user.getUserName());
                    // Ernakulam Routing
                    // Duplicate Check
                    Optional<ZoneErnakulam> dupOpt = ernakulamRepo.findAll().stream()
                        .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                     && z.getWards() != null && z.getWards().contains(ward))
                        .findFirst();
                    if (dupOpt.isPresent()) {
                        zoneId = dupOpt.get().getZoneId();
                    } else {
                        // Check for Space
                        Optional<ZoneErnakulam> spaceOpt = ernakulamRepo.findAll().stream()
                            .filter(z -> z.getLocalBodyName() != null && z.getLocalBodyName().equalsIgnoreCase(localBody)
                                         && z.getWards() != null && z.getWards().size() < 2)
                            .findFirst();
                        if (spaceOpt.isPresent()) {
                            ZoneErnakulam zone = spaceOpt.get();
                            if (zone.getWards() == null) {
                                zone.setWards(new ArrayList<>());
                            }
                            zone.getWards().add(ward);
                            if (zone.getWards().size() == 2) {
                                zone.setStatus("CLOSED");
                            }
                            ernakulamRepo.save(zone);
                            zoneId = zone.getZoneId();
                            anyChange = true;
                        } else {
                            // Create New Zone EKM_XXX
                            List<ZoneErnakulam> allE = ernakulamRepo.findAll();
                            int maxNum = 0;
                            for (ZoneErnakulam z : allE) {
                                String id = z.getZoneId();
                                if (id != null && id.startsWith("EKM_")) {
                                    try {
                                        int num = Integer.parseInt(id.substring(4));
                                        if (num > maxNum) maxNum = num;
                                    } catch (NumberFormatException e) {
                                        // ignore
                                    }
                                }
                            }
                            String newId = "EKM_" + String.format("%03d", maxNum + 1);
                            ZoneErnakulam newZone = new ZoneErnakulam(newId, localBody, ward);
                            newZone.setStatus("OPEN");
                            ernakulamRepo.save(newZone);
                            zoneId = newId;

                            // Initialize LastTimeCollection
                            if (!lastTimeCollectionRepository.existsById(newId)) {
                                LastTimeCollection ltc = new LastTimeCollection();
                                ltc.setZoneId(newId);
                                ltc.setLastCollectedDate(java.time.LocalDate.now().minusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                                ltc.setPrevious10Dates(new ArrayList<>());
                                lastTimeCollectionRepository.save(ltc);
                                newLastTimeCollectionsCreated.add(newId);
                            }
                            anyChange = true;
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Unknown or unsupported district: " + distName);
                }

                // Stage 3: Cross-Table Updates
                // 1. Update registered_user Table
                if (!zoneId.equals(user.getZoneId())) {
                    user.setZoneId(zoneId);
                    registeredUserRepository.save(user);
                    anyChange = true;
                }

                // 2. Update/Insert ZoneHouseDetail Table
                Optional<ZoneHouseDetail> detailOpt = zoneHouseDetailRepository.findById(userId);
                if (!detailOpt.isPresent()) {
                    // Insert new house details
                    ZoneHouseDetail newDetail = new ZoneHouseDetail();
                    newDetail.setRegisteredUserId(userId);
                    newDetail.setZoneId(zoneId);
                    newDetail.setWardNumber(ward);
                    newDetail.setHouseNumber(user.getHouseNumber());
                    newDetail.setOwnerName(user.getUserName());
                    newDetail.setLastCollectedDate(new ArrayList<>());
                    newDetail.setNextCollectedDate("Not scheduled");
                    newDetail.setCollectionStatus("pending");
                    newDetail.setAmountPending(0.0);
                    newDetail.setOtp(null);
                    zoneHouseDetailRepository.save(newDetail);
                    anyChange = true;
                } else {
                    // Update if changed
                    ZoneHouseDetail detail = detailOpt.get();
                    boolean changed = false;
                    if (!zoneId.equals(detail.getZoneId())) {
                        detail.setZoneId(zoneId);
                        changed = true;
                    }
                    if (ward != detail.getWardNumber()) {
                        detail.setWardNumber(ward);
                        changed = true;
                    }
                    if (changed) {
                        zoneHouseDetailRepository.save(detail);
                        anyChange = true;
                    }
                }
            }

            System.out.println("[ZONING ENGINE] Batch zoning completed with anyChange=" + anyChange);
            if (anyChange) {
                currentStatus = "Zone Updation/Creation is Complete.";
            } else {
                currentStatus = "Zone Updation/Creation was not needed.";
            }
            return anyChange ? "success" : "no_change";

        } catch (Exception e) {
            currentStatus = "ALERT: Zone Updation Operation Failure!";
            // ROLLBACK
            System.err.println("[ZONING ENGINE] Zoning failed, initiating rollback: " + e.getMessage());
            try {
                // Restore users
                registeredUserRepository.deleteAll();
                registeredUserRepository.saveAll(backupUsers);

                // Restore house details
                zoneHouseDetailRepository.deleteAll();
                zoneHouseDetailRepository.saveAll(backupHouseDetails);

                // Restore kottayam zones
                kottayamRepo.deleteAll();
                kottayamRepo.saveAll(backupKottayam);

                // Restore ernakulam zones
                ernakulamRepo.deleteAll();
                ernakulamRepo.saveAll(backupErnakulam);

                // Delete newly created auxiliary records
                for (String id : newLastTimeCollectionsCreated) {
                    lastTimeCollectionRepository.deleteById(id);
                }
                for (String id : newCollectionsCreated) {
                    registeredUserCollectionRepository.deleteById(id);
                }
                for (String id : newPaymentsCreated) {
                    registeredUserPaymentRepository.deleteById(id);
                }
                System.out.println("[ZONING ENGINE] Rollback completed successfully.");
            } catch (Exception re) {
                System.err.println("[ZONING ENGINE] Rollback failed critical error: " + re.getMessage());
            }
            throw e;
        } finally {
            zoningLock.writeLock().unlock();
        }
    }

    public void initializeUserRecords(RegisteredUser user) {
        String userId = user.getHouseId();
        if (!registeredUserCollectionRepository.existsById(userId)) {
            RegisteredUserCollection ruc = new RegisteredUserCollection(userId, "Not scheduled", new ArrayList<>(), "none");
            registeredUserCollectionRepository.save(ruc);
        }
        if (!registeredUserPaymentRepository.existsById(userId)) {
            RegisteredUserPayment rup = new RegisteredUserPayment(userId, 0.0, new ArrayList<>(), 0, 0);
            registeredUserPaymentRepository.save(rup);
        }
    }
}