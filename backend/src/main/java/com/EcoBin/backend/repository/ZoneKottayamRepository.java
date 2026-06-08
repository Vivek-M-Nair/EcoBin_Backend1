package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneKottayam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ZoneKottayamRepository extends MongoRepository<ZoneKottayam, String> {
    // Add this line to tell Spring Boot to support searching by local body name
    List<ZoneKottayam> findByLocalBodyName(String localBodyName);
}