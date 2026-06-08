package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

public abstract class ZoneDetails {
    @Id
    private String zoneId;

    @Field("local_body_name")
    private String localBodyName;

    @Field("assigned_wards")
    private List<Integer> wards = new ArrayList<>();

    @Field("zone_status")
    private String status; // "OPEN" or "CLOSED"

    public ZoneDetails() {
    }

    public ZoneDetails(String zoneId, String localBodyName, int firstWard) {
        this.zoneId = zoneId;
        this.localBodyName = localBodyName;
        this.wards.add(firstWard);
        this.status = "OPEN";
    }

    // Getters and Setters
    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getLocalBodyName() {
        return localBodyName;
    }

    public void setLocalBodyName(String localBodyName) {
        this.localBodyName = localBodyName;
    }

    public List<Integer> getWards() {
        return wards;
    }

    public void setWards(List<Integer> wards) {
        this.wards = wards;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}