package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.Complaint;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplaintRepository extends MongoRepository<Complaint, String> {
}
