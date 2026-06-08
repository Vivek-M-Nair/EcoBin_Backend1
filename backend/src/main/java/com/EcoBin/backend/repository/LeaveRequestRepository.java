package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.LeaveRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends MongoRepository<LeaveRequest, String> {
    List<LeaveRequest> findByStatus(String status);
    List<LeaveRequest> findByCollectionWorkerId(String collectionWorkerId);
}
