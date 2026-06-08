package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.CollectionWorker;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionWorkerRepository extends MongoRepository<CollectionWorker, String> {
    Optional<CollectionWorker> findByNameAndPhoneNumberAndEmailId(String name, String phoneNumber, String emailId);
    List<CollectionWorker> findByLocalBodyName(String localBodyName);
    List<CollectionWorker> findByDistrict(String district);
    List<CollectionWorker> findByRole(String role);
    List<CollectionWorker> findByDistrictAndRole(String district, String role);
    List<CollectionWorker> findByLocalBodyNameAndRole(String localBodyName, String role);
}
