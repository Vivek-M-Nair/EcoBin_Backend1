package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.LastTimeCollection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LastTimeCollectionRepository extends MongoRepository<LastTimeCollection, String> {
}
