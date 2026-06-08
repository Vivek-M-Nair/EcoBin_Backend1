package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.RegisteredUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisteredUserRepository extends MongoRepository<RegisteredUser, String> {

    Optional<RegisteredUser> findByHouseNumber(String houseNumber);

    List<RegisteredUser> findByZoneId(String zoneId);

    Optional<RegisteredUser> findByEmailId(String emailId);
}