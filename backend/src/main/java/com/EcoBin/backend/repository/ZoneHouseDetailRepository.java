package com.EcoBin.backend.repository;

import com.EcoBin.backend.Model.ZoneHouseDetail;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ZoneHouseDetailRepository extends MongoRepository<ZoneHouseDetail, String> {
    List<ZoneHouseDetail> findByZoneId(String zoneId);
    List<ZoneHouseDetail> findByZoneIdAndNextCollectedDate(String zoneId, String nextCollectedDate);
}
