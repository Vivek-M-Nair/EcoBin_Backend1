package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/register")
public class RegistrationController {

    @Autowired
    private HouseDetailsRepository houseDetailsRepository;

    @Autowired
    private StateDistRepository stateDistRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private CollectionWorkerRepository collectionWorkerRepository;

    @Autowired
    private ZoneKottayamRepository zonesKottayamRepository;

    @Autowired
    private ZoneErnakulamRepository zonesErnakulamRepository;

    // --- User Verification Request DTO ---
    public static class UserVerifyRequest {
        public String districtName;
        public String houseName;
        public String ownerName;
        public String houseNumber;
        public String subNo;
        public String localBodyName;
        public int wardNo;
    }

    @PostMapping("/user/verify")
    public ResponseEntity<?> verifyUserHouse(@RequestBody UserVerifyRequest request) {
        // Find district ID from district name
        Optional<StateDist> stateDistOpt = stateDistRepository.findByDistName(request.districtName);
        if (!stateDistOpt.isPresent()) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }
        String distId = stateDistOpt.get().getDistId();

        // Check in government house details database
        Optional<HouseDetails> houseOpt = houseDetailsRepository
                .findByHouseNumberAndHouseNameAndOwnerNameAndSubNoAndDistIdAndLocalBodyNameAndWardNo(
                        request.houseNumber, request.houseName, request.ownerName, request.subNo,
                        distId, request.localBodyName, request.wardNo);

        if (!houseOpt.isPresent()) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }

        // Check if already registered in Registered_user collection
        Optional<RegisteredUser> existingUserOpt = registeredUserRepository.findByHouseNumber(request.houseNumber);
        if (existingUserOpt.isPresent()) {
            RegisteredUser existingUser = existingUserOpt.get();
            // If the user already has a password set, they are fully active
            if (existingUser.getPassword() != null && !existingUser.getPassword().isEmpty()) {
                return ResponseEntity.ok("account already exist");
            }
            // If partial registration exists, return the assigned houseId
            return ResponseEntity.ok(Map.of("houseId", existingUser.getHouseId()));
        }

        // --- FIXED ZONING FIELD CALLS ---
        String zoneId = null;
        if ("Kottayam".equalsIgnoreCase(request.districtName) || "Dist-05".equalsIgnoreCase(distId)) {
            List<ZoneKottayam> zones = zonesKottayamRepository.findByLocalBodyName(request.localBodyName);
            for (ZoneKottayam z : zones) {
                if (z.getWards() != null && z.getWards().contains(request.wardNo)) { // Fixed from getWardNumbers()
                    zoneId = z.getZoneId();
                    break;
                }
            }
        } else if ("Ernakulam".equalsIgnoreCase(request.districtName) || "Dist-06".equalsIgnoreCase(distId)) {
            List<ZoneErnakulam> zones = zonesErnakulamRepository.findByLocalBodyName(request.localBodyName);
            for (ZoneErnakulam z : zones) {
                if (z.getWards() != null && z.getWards().contains(request.wardNo)) { // Fixed from getWardNumbers()
                    zoneId = z.getZoneId();
                    break;
                }
            }
        }

        // Generate clean unique house tracking ID
        String uniqueHouseId = "house-" + UUID.randomUUID().toString().substring(0, 8);

        // Save initial partial registration document
        RegisteredUser newUser = new RegisteredUser(uniqueHouseId, request.houseNumber, request.ownerName, zoneId);
        registeredUserRepository.save(newUser);

        return ResponseEntity.ok(Map.of("houseId", uniqueHouseId));
    }

    // --- User Registration Completion Request DTO ---
    public static class UserCompleteRequest {
        public String houseId;
        public String password;
        public String phoneNumber;
        public String emailId;
    }

    @PostMapping("/user/complete")
    public ResponseEntity<?> completeUserRegistration(@RequestBody UserCompleteRequest request) {
        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(request.houseId);
        if (!userOpt.isPresent()) {
            return ResponseEntity.status(404).body("User not found");
        }

        RegisteredUser user = userOpt.get();
        user.setPassword(request.password);
        user.setPhoneNumber(request.phoneNumber);
        user.setEmailId(request.emailId);
        registeredUserRepository.save(user);

        return ResponseEntity.ok("Registration complete status");
    }

    // --- Collection Worker Registration Request DTO ---
    public static class WorkerRegisterRequest {
        public String name;
        public String phoneNumber;
        public String emailId;
        public String district;
        public String localBodyName;
        public Integer ward;
        public Boolean consent;
    }

    @PostMapping("/worker")
    public ResponseEntity<?> registerCollectionWorker(@RequestBody WorkerRegisterRequest request) {
        Optional<CollectionWorker> existingOpt = collectionWorkerRepository
                .findByNameAndPhoneNumberAndEmailId(request.name, request.phoneNumber, request.emailId);

        if (existingOpt.isPresent()) {
            return ResponseEntity.ok("account already exist");
        }

        String workerId = "CW-" + (1000 + new Random().nextInt(9000));
        String generatedPassword = generateStrongPassword();

        CollectionWorker worker = new CollectionWorker(
                workerId, request.name, request.phoneNumber, request.emailId,
                request.district, request.localBodyName, request.ward, request.consent,
                generatedPassword, "WORKER");

        // Assigning metrics matching your operational region data
        worker.setScheduledZoneName("Zone-1-" + request.district);
        worker.setNumberOfHouses(15);
        worker.setWardNumber(request.ward != null ? request.ward : 1);
        worker.setVillageName(request.localBodyName);

        collectionWorkerRepository.save(worker);

        return ResponseEntity.ok(Map.of(
                "collection_worker_id", workerId,
                "password", generatedPassword));
    }

    // --- Office Staff Registration Request DTO ---
    public static class StaffRegisterRequest {
        public String name;
        public String phoneNumber;
        public String emailId;
        public String district;
        public Boolean consent;
    }

    @PostMapping("/staff")
    public ResponseEntity<?> registerOfficeStaff(@RequestBody StaffRegisterRequest request) {
        Optional<CollectionWorker> existingOpt = collectionWorkerRepository
                .findByNameAndPhoneNumberAndEmailId(request.name, request.phoneNumber, request.emailId);

        if (existingOpt.isPresent()) {
            return ResponseEntity.ok("account already exist");
        }

        String staffId = "CS-" + (1000 + new Random().nextInt(9000));
        String generatedPassword = generateStrongPassword();

        CollectionWorker staff = new CollectionWorker(
                staffId, request.name, request.phoneNumber, request.emailId,
                request.district, null, null, request.consent,
                generatedPassword, "OFFICE_STAFF");

        staff.setScheduledZoneName("N/A");
        staff.setNumberOfHouses(0);
        staff.setWardNumber(0);
        staff.setVillageName("N/A");

        collectionWorkerRepository.save(staff);

        return ResponseEntity.ok(Map.of(
                "collection_worker_id", staffId,
                "password", generatedPassword));
    }

    private String generateStrongPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}