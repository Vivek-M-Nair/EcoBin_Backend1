package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneErnakulam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.Optional;

public interface ZoneErnakulamRepository extends MongoRepository<ZoneErnakulam, String> {
    Optional<ZoneErnakulam> findByLocalBodyNameAndStatus(String localBodyName, String status);

    @Query("{ 'local_body_name': ?0, 'assigned_wards': ?1 }")
    boolean existsByLocalBodyNameAndWard(String localBodyName, int ward);
}