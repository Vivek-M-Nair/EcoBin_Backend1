package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.Report;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {
    List<Report> findByWorkerId(String workerId);
    List<Report> findByZoneId(String zoneId);
    List<Report> findByDate(String date);
}
