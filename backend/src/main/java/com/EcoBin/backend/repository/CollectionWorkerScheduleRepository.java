package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.CollectionWorkerSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionWorkerScheduleRepository extends MongoRepository<CollectionWorkerSchedule, String> {
    List<CollectionWorkerSchedule> findByAssignedPanchayath(String assignedPanchayath);
    List<CollectionWorkerSchedule> findByAssignedZoneId(String zoneId);
    Optional<CollectionWorkerSchedule> findByCollectionWorkerIdAndAssignedZoneId(String workerId, String zoneId);
}
