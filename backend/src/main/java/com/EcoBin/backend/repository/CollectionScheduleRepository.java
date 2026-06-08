package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.CollectionSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionScheduleRepository extends MongoRepository<CollectionSchedule, String> {
    List<CollectionSchedule> findByZoneId(String zoneId);
    Optional<CollectionSchedule> findByZoneIdAndScheduledDate(String zoneId, String scheduledDate);
    List<CollectionSchedule> findByCollectionWorkerAssignedAndScheduledDate(String workerId, String date);
    List<CollectionSchedule> findByScheduledDate(String scheduledDate);
    List<CollectionSchedule> findByCollectionWorkerAssigned(String workerId);
    List<CollectionSchedule> findByDistrict(String district);
    List<CollectionSchedule> findByLocalBodyName(String localBodyName);
}
