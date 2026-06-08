package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "Last_time_collection")
public class LastTimeCollection {
    @Id
    private String zoneId;

    @Field("last_collected_date")
    private String lastCollectedDate;

    @Field("previous_10_dates")
    private List<String> previous10Dates = new ArrayList<>();

    public LastTimeCollection() {}

    public LastTimeCollection(String zoneId, String lastCollectedDate, List<String> previous10Dates) {
        this.zoneId = zoneId;
        this.lastCollectedDate = lastCollectedDate;
        this.previous10Dates = previous10Dates;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getLastCollectedDate() {
        return lastCollectedDate;
    }

    public void setLastCollectedDate(String lastCollectedDate) {
        this.lastCollectedDate = lastCollectedDate;
    }

    public List<String> getPrevious10Dates() {
        return previous10Dates;
    }

    public void setPrevious10Dates(List<String> previous10Dates) {
        this.previous10Dates = previous10Dates;
    }
}
