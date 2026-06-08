package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "immediate_collection_reason")
public class ImmediateCollectionReason {
    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("reason")
    private String reason;

    @Field("requested_date")
    private String requestedDate;

    public ImmediateCollectionReason() {}

    public ImmediateCollectionReason(String id, String userId, String reason, String requestedDate) {
        this.id = id;
        this.userId = userId;
        this.reason = reason;
        this.requestedDate = requestedDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(String requestedDate) {
        this.requestedDate = requestedDate;
    }
}
