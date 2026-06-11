package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.UserRequestedData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRequestedDataRepository extends MongoRepository<UserRequestedData, String> {
    List<UserRequestedData> findByUserId(String userId);
    List<UserRequestedData> findByWorkerIdAndNextScheduledDate(String workerId, String nextScheduledDate);
}
