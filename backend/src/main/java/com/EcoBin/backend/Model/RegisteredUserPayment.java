package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "registered_user_payment")
public class RegisteredUserPayment {
    @Id
    private String userId;

    @Field("Amount_pending")
    private double amountPending;

    @Field("last_10_times_details")
    private List<String> last10TimesDetails = new ArrayList<>(); // List of status like "Paid", "Pending", etc.

    @Field("point_earned")
    private int pointEarned;

    @Field("pending_point")
    private int pendingPoint;

    public RegisteredUserPayment() {}

    public RegisteredUserPayment(String userId, double amountPending, List<String> last10TimesDetails, int pointEarned, int pendingPoint) {
        this.userId = userId;
        this.amountPending = amountPending;
        this.last10TimesDetails = last10TimesDetails;
        this.pointEarned = pointEarned;
        this.pendingPoint = pendingPoint;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getAmountPending() {
        return amountPending;
    }

    public void setAmountPending(double amountPending) {
        this.amountPending = amountPending;
    }

    public List<String> getLast10TimesDetails() {
        return last10TimesDetails;
    }

    public void setLast10TimesDetails(List<String> last10TimesDetails) {
        this.last10TimesDetails = last10TimesDetails;
    }

    public int getPointEarned() {
        return pointEarned;
    }

    public void setPointEarned(int pointEarned) {
        this.pointEarned = pointEarned;
    }

    public int getPendingPoint() {
        return pendingPoint;
    }

    public void setPendingPoint(int pendingPoint) {
        this.pendingPoint = pendingPoint;
    }
}
