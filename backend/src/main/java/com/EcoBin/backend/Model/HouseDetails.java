package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "House_details")
public class HouseDetails {

    @Id
    private String houseNumber; // Maps directly to MongoDB's "_id" ("H-101")

    @Field("house_name")
    private String houseName;

    @Field("Owner_name")
    private String ownerName;

    @Field("Sub_no")
    private String subNo;

    @Field("dist_id")
    private String distId;

    @Field("panchayath_or_municipality_name")
    private String localBodyName;

    @Field("Ward_no")
    private int wardNo;

    // Default Constructor
    public HouseDetails() {
    }

    public HouseDetails(String houseNumber, String houseName, String ownerName, String subNo,
            String distId, String localBodyName, int wardNo) {
        this.houseNumber = houseNumber;
        this.houseName = houseName;
        this.ownerName = ownerName;
        this.subNo = subNo;
        this.distId = distId;
        this.localBodyName = localBodyName;
        this.wardNo = wardNo;
    }

    // Getters and Setters
    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getHouseName() {
        return houseName;
    }

    public void setHouseName(String houseName) {
        this.houseName = houseName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getSubNo() {
        return subNo;
    }

    public void setSubNo(String subNo) {
        this.subNo = subNo;
    }

    public String getDistId() {
        return distId;
    }

    public void setDistId(String distId) {
        this.distId = distId;
    }

    public String getLocalBodyName() {
        return localBodyName;
    }

    public void setLocalBodyName(String localBodyName) {
        this.localBodyName = localBodyName;
    }

    public int getWardNo() {
        return wardNo;
    }

    public void setWardNo(int wardNo) {
        this.wardNo = wardNo;
    }
}