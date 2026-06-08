package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "collection_worker_schedule")
public class CollectionWorkerSchedule {
    @Id
    private String collectionWorkerId;

    @Field("name")
    private String name;

    @Field("number_of_assigned_dates")
    private int numberOfAssignedDates;

    @Field("assigned_panchayath")
    private String assignedPanchayath;

    @Field("assigned_zone_id")
    private String assignedZoneId;

    @Field("leave_requested_date")
    private String leaveRequestedDate; // Updated if leave is approved

    @Field("assigned_dates")
    private List<String> assignedDates = new ArrayList<>(); // List of dates they are assigned to collect

    public CollectionWorkerSchedule() {}

    public CollectionWorkerSchedule(String collectionWorkerId, String name, int numberOfAssignedDates,
                                    String assignedPanchayath, String assignedZoneId, String leaveRequestedDate) {
        this.collectionWorkerId = collectionWorkerId;
        this.name = name;
        this.numberOfAssignedDates = numberOfAssignedDates;
        this.assignedPanchayath = assignedPanchayath;
        this.assignedZoneId = assignedZoneId;
        this.leaveRequestedDate = leaveRequestedDate;
    }

    public String getCollectionWorkerId() {
        return collectionWorkerId;
    }

    public void setCollectionWorkerId(String collectionWorkerId) {
        this.collectionWorkerId = collectionWorkerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfAssignedDates() {
        return numberOfAssignedDates;
    }

    public void setNumberOfAssignedDates(int numberOfAssignedDates) {
        this.numberOfAssignedDates = numberOfAssignedDates;
    }

    public String getAssignedPanchayath() {
        return assignedPanchayath;
    }

    public void setAssignedPanchayath(String assignedPanchayath) {
        this.assignedPanchayath = assignedPanchayath;
    }

    public String getAssignedZoneId() {
        return assignedZoneId;
    }

    public void setAssignedZoneId(String assignedZoneId) {
        this.assignedZoneId = assignedZoneId;
    }

    public String getLeaveRequestedDate() {
        return leaveRequestedDate;
    }

    public void setLeaveRequestedDate(String leaveRequestedDate) {
        this.leaveRequestedDate = leaveRequestedDate;
    }

    public List<String> getAssignedDates() {
        return assignedDates;
    }

    public void setAssignedDates(List<String> assignedDates) {
        this.assignedDates = assignedDates;
    }
}
