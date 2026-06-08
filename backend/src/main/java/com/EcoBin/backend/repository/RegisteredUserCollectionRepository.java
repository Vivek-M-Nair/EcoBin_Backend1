package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.RegisteredUserCollection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegisteredUserCollectionRepository extends MongoRepository<RegisteredUserCollection, String> {
}
