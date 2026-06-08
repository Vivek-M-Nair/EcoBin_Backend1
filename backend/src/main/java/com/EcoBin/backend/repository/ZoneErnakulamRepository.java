package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneErnakulam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ZoneErnakulamRepository extends MongoRepository<ZoneErnakulam, String> {
    // Add this line
    List<ZoneErnakulam> findByLocalBodyName(String localBodyName);
}