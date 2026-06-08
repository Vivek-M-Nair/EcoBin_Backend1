package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.HouseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HouseDetailsRepository extends MongoRepository<HouseDetails, String> {
    Optional<HouseDetails> findByHouseNumberAndHouseNameAndOwnerNameAndSubNoAndDistIdAndLocalBodyNameAndWardNo(
            String houseNumber, String houseName, String ownerName, String subNo, String distId, String localBodyName, int wardNo
    );
}