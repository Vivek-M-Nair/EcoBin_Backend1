package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.StateDist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StateDistRepository extends MongoRepository<StateDist, String> {
    Optional<StateDist> findByDistName(String distName);
}