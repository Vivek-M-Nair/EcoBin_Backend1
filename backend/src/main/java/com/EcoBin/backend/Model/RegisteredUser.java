package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "Registered_user")
public class RegisteredUser {

    @Id
    private String houseId;

    @Field("house_number")
    private String houseNumber;

    @Field("house_name")
    private String houseName;

    @Field("sub_no")
    private String subNo;

    @Field("dist_id")
    private String distId;

    @Field("panchayath_or_municipality_name")
    private String panchayathOrMunicipalityName;

    @Field("ward_no")
    private int wardNo;

    @Field("user_name")
    private String userName;

    private String password;

    @Field("phone_number")
    private String phoneNumber;

    @Field("email_id")
    private String emailId;

    @Field("zone_id")
    private String zoneId;

    @Field("pending_payment")
    private double pendingPayment = 0.0;

    private int points = 0;

    // Default Constructor
    public RegisteredUser() {
    }

    // Parameterized Constructor including new house details
    public RegisteredUser(String houseId, String houseNumber, String houseName, String subNo,
            String distId, String panchayathOrMunicipalityName, int wardNo,
            String userName, String zoneId) {
        this.houseId = houseId;
        this.houseNumber = houseNumber;
        this.houseName = houseName;
        this.subNo = subNo;
        this.distId = distId;
        this.panchayathOrMunicipalityName = panchayathOrMunicipalityName;
        this.wardNo = wardNo;
        this.userName = userName;
        this.zoneId = zoneId;
        this.pendingPayment = 0.0;
        this.points = 0;
    }

    // Getters and Setters
    public String getHouseId() {
        return houseId;
    }

    public void setHouseId(String houseId) {
        this.houseId = houseId;
    }

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

    public String getPanchayathOrMunicipalityName() {
        return panchayathOrMunicipalityName;
    }

    public void setPanchayathOrMunicipalityName(String panchayathOrMunicipalityName) {
        this.panchayathOrMunicipalityName = panchayathOrMunicipalityName;
    }

    public int getWardNo() {
        return wardNo;
    }

    public void setWardNo(int wardNo) {
        this.wardNo = wardNo;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public double getPendingPayment() {
        return pendingPayment;
    }

    public void setPendingPayment(double pendingPayment) {
        this.pendingPayment = pendingPayment;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }
}