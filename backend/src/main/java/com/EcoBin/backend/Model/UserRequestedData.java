package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "user_requested_data")
public class UserRequestedData {
    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("zone_id")
    private String zoneId;

    @Field("payment_amount")
    private double paymentAmount = 50.0;

    @Field("payment_status")
    private String paymentStatus = "success";

    @Field("requested_date")
    private String requestedDate;

    @Field("last_collected_date_in_zone")
    private String lastCollectedDateInZone;

    @Field("next_scheduled_date")
    private String nextScheduledDate;

    @Field("validate_otp")
    private String validateOtp = null;

    @Field("otp_gen")
    private String otpGen = null;

    @Field("status")
    private String status = "pending"; // pending, accepted, declined, collected

    @Field("worker_id")
    private String workerId = null;

    public UserRequestedData() {}

    public UserRequestedData(String id, String userId, String zoneId, double paymentAmount,
                             String paymentStatus, String requestedDate, String lastCollectedDateInZone,
                             String nextScheduledDate, String validateOtp, String otpGen, String status, String workerId) {
        this.id = id;
        this.userId = userId;
        this.zoneId = zoneId;
        this.paymentAmount = paymentAmount;
        this.paymentStatus = paymentStatus;
        this.requestedDate = requestedDate;
        this.lastCollectedDateInZone = lastCollectedDateInZone;
        this.nextScheduledDate = nextScheduledDate;
        this.validateOtp = validateOtp;
        this.otpGen = otpGen;
        this.status = status;
        this.workerId = workerId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public double getPaymentAmount() { return paymentAmount; }
    public void setPaymentAmount(double paymentAmount) { this.paymentAmount = paymentAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getRequestedDate() { return requestedDate; }
    public void setRequestedDate(String requestedDate) { this.requestedDate = requestedDate; }

    public String getLastCollectedDateInZone() { return lastCollectedDateInZone; }
    public void setLastCollectedDateInZone(String lastCollectedDateInZone) { this.lastCollectedDateInZone = lastCollectedDateInZone; }

    public String getNextScheduledDate() { return nextScheduledDate; }
    public void setNextScheduledDate(String nextScheduledDate) { this.nextScheduledDate = nextScheduledDate; }

    public String getValidateOtp() { return validateOtp; }
    public void setValidateOtp(String validateOtp) { this.validateOtp = validateOtp; }

    public String getOtpGen() { return otpGen; }
    public void setOtpGen(String otpGen) { this.otpGen = otpGen; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
}
