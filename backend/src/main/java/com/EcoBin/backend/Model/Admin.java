package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "admin")
public class Admin {

    @Id
    private String userId;

    @Field("Admin_name")
    private String adminName;

    private String password;

    public Admin() {}

    public Admin(String userId, String adminName, String password) {
        this.userId = userId;
        this.adminName = adminName;
        this.password = password;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
