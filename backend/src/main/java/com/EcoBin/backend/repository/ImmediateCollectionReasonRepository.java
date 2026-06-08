package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ImmediateCollectionReason;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ImmediateCollectionReasonRepository extends MongoRepository<ImmediateCollectionReason, String> {
    List<ImmediateCollectionReason> findByUserId(String userId);
}
