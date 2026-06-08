package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "Complaints")
public class Complaint {
    @Id
    private String complaintId;

    @Field("Complain_description")
    private String complaintDescription;

    @Field("Image")
    private String image; // Base64 or URL

    @Field("status")
    private String status = "pending";

    public Complaint() {}

    public Complaint(String complaintId, String complaintDescription, String image, String status) {
        this.complaintId = complaintId;
        this.complaintDescription = complaintDescription;
        this.image = image;
        this.status = status;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public String getComplaintDescription() {
        return complaintDescription;
    }

    public void setComplaintDescription(String complaintDescription) {
        this.complaintDescription = complaintDescription;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
