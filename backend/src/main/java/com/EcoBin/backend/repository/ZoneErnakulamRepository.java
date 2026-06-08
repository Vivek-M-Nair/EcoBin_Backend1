package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneErnakulam;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ZoneErnakulamRepository extends MongoRepository<ZoneErnakulam, String> {
    Optional<ZoneErnakulam> findByLocalBodyNameAndStatus(String localBodyName, String status);

    boolean existsByLocalBodyNameAndWardsIn(String localBodyName, int ward);
}