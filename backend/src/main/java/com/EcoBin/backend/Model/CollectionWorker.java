package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "Collection_workers")
public class CollectionWorker {

    @Id
    private String collectionWorkerId;

    private String name;

    @Field("phone_number")
    private String phoneNumber;

    @Field("email_id")
    private String emailId;

    private String district;

    @Field("local_body_name")
    private String localBodyName;

    private Integer ward;

    private Boolean consent;

    private String password;

    private String role; // "WORKER" or "OFFICE_STAFF"

    @Field("scheduled_zone_name")
    private String scheduledZoneName;

    @Field("number_of_houses")
    private Integer numberOfHouses;

    @Field("ward_number")
    private Integer wardNumber;

    @Field("village_name")
    private String villageName;

    public CollectionWorker() {}

    public CollectionWorker(String collectionWorkerId, String name, String phoneNumber, String emailId,
                            String district, String localBodyName, Integer ward, Boolean consent,
                            String password, String role) {
        this.collectionWorkerId = collectionWorkerId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.emailId = emailId;
        this.district = district;
        this.localBodyName = localBodyName;
        this.ward = ward;
        this.consent = consent;
        this.password = password;
        this.role = role;
    }

    // Getters and Setters
    public String getCollectionWorkerId() { return collectionWorkerId; }
    public void setCollectionWorkerId(String collectionWorkerId) { this.collectionWorkerId = collectionWorkerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getLocalBodyName() { return localBodyName; }
    public void setLocalBodyName(String localBodyName) { this.localBodyName = localBodyName; }

    public Integer getWard() { return ward; }
    public void setWard(Integer ward) { this.ward = ward; }

    public Boolean getConsent() { return consent; }
    public void setConsent(Boolean consent) { this.consent = consent; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getScheduledZoneName() { return scheduledZoneName; }
    public void setScheduledZoneName(String scheduledZoneName) { this.scheduledZoneName = scheduledZoneName; }

    public Integer getNumberOfHouses() { return numberOfHouses; }
    public void setNumberOfHouses(Integer numberOfHouses) { this.numberOfHouses = numberOfHouses; }

    public Integer getWardNumber() { return wardNumber; }
    public void setWardNumber(Integer wardNumber) { this.wardNumber = wardNumber; }

    public String getVillageName() { return villageName; }
    public void setVillageName(String villageName) { this.villageName = villageName; }
}
