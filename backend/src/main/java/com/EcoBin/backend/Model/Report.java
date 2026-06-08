package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "report")
public class Report {
    @Id
    private String reportId;

    @Field("worker_id")
    private String workerId;

    @Field("zone_id")
    private String zoneId;

    @Field("date")
    private String date;

    @Field("total_house_collected")
    private int totalHouseCollected;

    @Field("cash_collected")
    private double cashCollected;

    @Field("total_pending_house")
    private int totalPendingHouse;

    @Field("reason_for_pending")
    private String reasonForPending;

    public Report() {}

    public Report(String reportId, String workerId, String zoneId, String date, int totalHouseCollected, double cashCollected, int totalPendingHouse, String reasonForPending) {
        this.reportId = reportId;
        this.workerId = workerId;
        this.zoneId = zoneId;
        this.date = date;
        this.totalHouseCollected = totalHouseCollected;
        this.cashCollected = cashCollected;
        this.totalPendingHouse = totalPendingHouse;
        this.reasonForPending = reasonForPending;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getTotalHouseCollected() {
        return totalHouseCollected;
    }

    public void setTotalHouseCollected(int totalHouseCollected) {
        this.totalHouseCollected = totalHouseCollected;
    }

    public double getCashCollected() {
        return cashCollected;
    }

    public void setCashCollected(double cashCollected) {
        this.cashCollected = cashCollected;
    }

    public int getTotalPendingHouse() {
        return totalPendingHouse;
    }

    public void setTotalPendingHouse(int totalPendingHouse) {
        this.totalPendingHouse = totalPendingHouse;
    }

    public String getReasonForPending() {
        return reasonForPending;
    }

    public void setReasonForPending(String reasonForPending) {
        this.reasonForPending = reasonForPending;
    }
}
