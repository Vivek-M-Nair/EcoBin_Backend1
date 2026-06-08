package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "Office_staff")
public class OfficeStaff {

    @Id
    private String userId; // Maps to MongoDB's natural unique identifier (_id)

    @Field("Full Name")
    private String fullName;

    @Field("Designation")
    private String designation;

    @Field("Email")
    private String email;

    @Field("Phone No")
    private String phoneNo;

    @Field("Password")
    private String password;

    // Default Constructor
    public OfficeStaff() {
    }

    // Parameterized Constructor
    public OfficeStaff(String userId, String fullName, String designation, String email, String phoneNo,
            String password) {
        this.userId = userId;
        this.fullName = fullName;
        this.designation = designation;
        this.email = email;
        this.phoneNo = phoneNo;
        this.password = password;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}