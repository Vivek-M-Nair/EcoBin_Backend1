package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "Collection_Schedule")
public class CollectionSchedule {

    @Id
    private String scheduleId;

    @Field("zone_id")
    private String zoneId;

    @Field("scheduled_date")
    private String scheduledDate;

    @Field("collection_worker_assigned")
    private String collectionWorkerAssigned;

    @Field("amount_per_house")
    private double amountPerHouse = 50.0;

    @Field("local_body_name")
    private String localBodyName;

    @Field("district")
    private String district;

    @Field("status")
    private String status = "scheduled"; // scheduled, in_progress, completed

    public CollectionSchedule() {}

    public CollectionSchedule(String scheduleId, String zoneId, String scheduledDate,
                              String collectionWorkerAssigned, double amountPerHouse,
                              String localBodyName, String district) {
        this.scheduleId = scheduleId;
        this.zoneId = zoneId;
        this.scheduledDate = scheduledDate;
        this.collectionWorkerAssigned = collectionWorkerAssigned;
        this.amountPerHouse = amountPerHouse;
        this.localBodyName = localBodyName;
        this.district = district;
    }

    // Getters and Setters
    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = scheduleId; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(String scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getCollectionWorkerAssigned() { return collectionWorkerAssigned; }
    public void setCollectionWorkerAssigned(String collectionWorkerAssigned) { this.collectionWorkerAssigned = collectionWorkerAssigned; }

    public double getAmountPerHouse() { return amountPerHouse; }
    public void setAmountPerHouse(double amountPerHouse) { this.amountPerHouse = amountPerHouse; }

    public String getLocalBodyName() { return localBodyName; }
    public void setLocalBodyName(String localBodyName) { this.localBodyName = localBodyName; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
