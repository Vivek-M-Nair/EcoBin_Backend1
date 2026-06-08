package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneKottayam;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ZoneKottayamRepository extends MongoRepository<ZoneKottayam, String> {
    Optional<ZoneKottayam> findByLocalBodyNameAndStatus(String localBodyName, String status);

    boolean existsByLocalBodyNameAndWardsIn(String localBodyName, int ward);
}