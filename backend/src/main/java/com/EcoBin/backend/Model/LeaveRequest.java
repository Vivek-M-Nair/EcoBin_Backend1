package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "leave_requests")
public class LeaveRequest {
    @Id
    private String requestId;

    @Field("collection_worker_id")
    private String collectionWorkerId;

    @Field("name")
    private String name;

    @Field("leave_requested_date")
    private String leaveRequestedDate;

    @Field("reason")
    private String reason;

    @Field("status")
    private String status = "pending"; // pending, approved, rejected

    public LeaveRequest() {}

    public LeaveRequest(String requestId, String collectionWorkerId, String name, String leaveRequestedDate, String reason, String status) {
        this.requestId = requestId;
        this.collectionWorkerId = collectionWorkerId;
        this.name = name;
        this.leaveRequestedDate = leaveRequestedDate;
        this.reason = reason;
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public String getLeaveRequestedDate() {
        return leaveRequestedDate;
    }

    public void setLeaveRequestedDate(String leaveRequestedDate) {
        this.leaveRequestedDate = leaveRequestedDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
