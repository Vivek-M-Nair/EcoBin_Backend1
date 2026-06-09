package com.EcoBin.backend.controller;

import com.EcoBin.backend.Model.*;
import com.EcoBin.backend.repository.*;
import com.EcoBin.backend.service.ZoningService;
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
    private OfficeStaffRepository officeStaffRepository; // Linked your new repository here

    @Autowired
    private ZoningService zoningService;

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
        if (request.districtName == null || request.houseNumber == null) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }

        Optional<StateDist> stateDistOpt = stateDistRepository.findAll().stream()
                .filter(sd -> sd.getDistName() != null && sd.getDistName().trim().equalsIgnoreCase(request.districtName.trim()))
                .findFirst();

        if (!stateDistOpt.isPresent()) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }
        String distId = stateDistOpt.get().getDistId();

        Optional<HouseDetails> houseOpt = houseDetailsRepository.findById(request.houseNumber.trim());

        if (!houseOpt.isPresent()) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }

        HouseDetails house = houseOpt.get();

        boolean isMatch = trimIgnoreCase(house.getHouseName()).equals(trimIgnoreCase(request.houseName))
                && trimIgnoreCase(house.getOwnerName()).equals(trimIgnoreCase(request.ownerName))
                && trimIgnoreCase(house.getSubNo()).equals(trimIgnoreCase(request.subNo))
                && trimIgnoreCase(house.getDistId()).equals(trimIgnoreCase(distId))
                && trimIgnoreCase(house.getLocalBodyName()).equals(trimIgnoreCase(request.localBodyName))
                && house.getWardNo() == request.wardNo;

        if (!isMatch) {
            return ResponseEntity.ok("House not found_please Register your house with Government!");
        }

        Optional<RegisteredUser> existingUserOpt = registeredUserRepository.findByHouseNumber(request.houseNumber.trim());
        if (existingUserOpt.isPresent()) {
            RegisteredUser existingUser = existingUserOpt.get();
            if (existingUser.getPassword() != null && !existingUser.getPassword().isEmpty()) {
                return ResponseEntity.ok("account already exist");
            }
            return ResponseEntity.ok(Map.of("houseId", existingUser.getHouseId()));
        }

        String uniqueHouseId = "house-" + UUID.randomUUID().toString().substring(0, 8);

        RegisteredUser newUser = new RegisteredUser();
        newUser.setHouseId(uniqueHouseId);
        newUser.setHouseNumber(request.houseNumber.trim());
        newUser.setUserName(house.getOwnerName()); // Use official government database owner name
        newUser.setZoneId("UNZONED");

        newUser.setHouseName(house.getHouseName());
        newUser.setSubNo(house.getSubNo());
        newUser.setDistId(distId);
        newUser.setPanchayathOrMunicipalityName(house.getLocalBodyName());
        newUser.setWardNo(house.getWardNo());
        newUser.setPendingPayment(0.0);
        newUser.setPoints(0);

        registeredUserRepository.save(newUser);

        return ResponseEntity.ok(Map.of("houseId", uniqueHouseId));
    }

    private String trimIgnoreCase(String val) {
        return val == null ? "" : val.trim().toLowerCase();
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
        Optional<RegisteredUser> emailCheckOpt = registeredUserRepository.findByEmailId(request.emailId);
        if (emailCheckOpt.isPresent() && emailCheckOpt.get().getPassword() != null) {
            return ResponseEntity.badRequest().body("Email identifier already registered to an account");
        }

        Optional<RegisteredUser> userOpt = registeredUserRepository.findById(request.houseId);
        if (!userOpt.isPresent()) {
            return ResponseEntity.status(404).body("User not found");
        }

        RegisteredUser user = userOpt.get();
        user.setPassword(request.password);
        user.setPhoneNumber(request.phoneNumber);
        user.setEmailId(request.emailId);

        // Assign zone using concurrent ward zoning logic
        String zoneId = zoningService.assignUserToZone(user);
        user.setZoneId(zoneId);

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

        worker.setScheduledZoneName("Zone-1-" + request.district);
        worker.setNumberOfHouses(15);
        worker.setWardNumber(request.ward != null ? request.ward : 1);
        worker.setVillageName(request.localBodyName);

        collectionWorkerRepository.save(worker);

        return ResponseEntity.ok(Map.of(
                "collection_worker_id", workerId,
                "password", generatedPassword));
    }

    // --- Office Staff Registration Request DTO (Washed down fields) ---
    public static class StaffRegisterRequest {
        public String name;
        public String designation;
        public String phoneNumber;
        public String emailId;
    }

    @PostMapping("/staff")
    public ResponseEntity<?> registerOfficeStaff(@RequestBody StaffRegisterRequest request) {
        // Look up against your separate office staff directory
        Optional<OfficeStaff> existingOpt = officeStaffRepository.findByEmail(request.emailId);

        if (existingOpt.isPresent()) {
            return ResponseEntity.ok("account already exist");
        }

        String staffId = "CS-" + (1000 + new Random().nextInt(9000));
        String generatedPassword = generateStrongPassword();

        // Building an entity targeting your standalone document model configuration
        OfficeStaff staff = new OfficeStaff(
                staffId,
                request.name,
                request.designation != null ? request.designation : "Office Staff",
                request.emailId,
                request.phoneNumber,
                generatedPassword);

        // Saves precisely inside the "Office_staff" collection space
        officeStaffRepository.save(staff);

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