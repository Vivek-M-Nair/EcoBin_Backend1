package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneKottayam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.Optional;

public interface ZoneKottayamRepository extends MongoRepository<ZoneKottayam, String> {
    Optional<ZoneKottayam> findByLocalBodyNameAndStatus(String localBodyName, String status);

    @Query("{ 'local_body_name': ?0, 'assigned_wards': ?1 }")
    boolean existsByLocalBodyNameAndWard(String localBodyName, int ward);
}