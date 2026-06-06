package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.HouseDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HouseDetailsRepository extends MongoRepository<HouseDetails, String> {
}