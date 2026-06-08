package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.CollectionExpense;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollectionExpenseRepository extends MongoRepository<CollectionExpense, String> {
    List<CollectionExpense> findByCollectionWorkerId(String collectionWorkerId);
    List<CollectionExpense> findByStatus(String status);
}
