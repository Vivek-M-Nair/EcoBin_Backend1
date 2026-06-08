package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.RegisteredUserPayment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegisteredUserPaymentRepository extends MongoRepository<RegisteredUserPayment, String> {
}
