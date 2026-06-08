package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.OfficeStaff;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficeStaffRepository extends MongoRepository<OfficeStaff, String> {

    // Custom helper query to find staff by email (useful for login/validation)
    Optional<OfficeStaff> findByEmail(String email);

    // Custom helper query to find staff by phone number
    Optional<OfficeStaff> findByPhoneNo(String phoneNo);
}