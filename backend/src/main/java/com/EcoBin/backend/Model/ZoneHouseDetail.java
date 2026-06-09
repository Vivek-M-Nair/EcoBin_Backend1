package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "Zone_house_detail")
public class ZoneHouseDetail {
    @Id
    private String registeredUserId;

    @Field("zone_id")
    private String zoneId;

    @Field("ward_number")
    private int wardNumber;

    @Field("House_number")
    private String houseNumber;

    @Field("Owner_name")
    private String ownerName;

    @Field("last_collected_date")
    private List<String> lastCollectedDate = new ArrayList<>();

    @Field("next_collected_date")
    private String nextCollectedDate;

    @Field("Collection_status")
    private String collectionStatus; // e.g. "pending", "collected", etc.

    @Field("Amount_pending")
    private double amountPending;

    @Field("otp")
    private String otp;

    public ZoneHouseDetail() {}

    public ZoneHouseDetail(String registeredUserId, String zoneId, int wardNumber, String houseNumber,
                           String ownerName, List<String> lastCollectedDate, String nextCollectedDate,
                           String collectionStatus, double amountPending, String otp) {
        this.registeredUserId = registeredUserId;
        this.zoneId = zoneId;
        this.wardNumber = wardNumber;
        this.houseNumber = houseNumber;
        this.ownerName = ownerName;
        this.lastCollectedDate = lastCollectedDate;
        this.nextCollectedDate = nextCollectedDate;
        this.collectionStatus = collectionStatus;
        this.amountPending = amountPending;
        this.otp = otp;
    }

    public String getRegisteredUserId() {
        return registeredUserId;
    }

    public void setRegisteredUserId(String registeredUserId) {
        this.registeredUserId = registeredUserId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public int getWardNumber() {
        return wardNumber;
    }

    public void setWardNumber(int wardNumber) {
        this.wardNumber = wardNumber;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<String> getLastCollectedDate() {
        return lastCollectedDate;
    }

    public void setLastCollectedDate(List<String> lastCollectedDate) {
        this.lastCollectedDate = lastCollectedDate;
    }

    public String getNextCollectedDate() {
        return nextCollectedDate;
    }

    public void setNextCollectedDate(String nextCollectedDate) {
        this.nextCollectedDate = nextCollectedDate;
    }

    public String getCollectionStatus() {
        return collectionStatus;
    }

    public void setCollectionStatus(String collectionStatus) {
        this.collectionStatus = collectionStatus;
    }

    public double getAmountPending() {
        return amountPending;
    }

    public void setAmountPending(double amountPending) {
        this.amountPending = amountPending;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
