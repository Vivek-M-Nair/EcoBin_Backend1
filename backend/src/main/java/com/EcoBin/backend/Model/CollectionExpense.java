package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "collection_Expenses")
public class CollectionExpense {
    @Id
    private String expenseId;

    @Field("collection_worker_id")
    private String collectionWorkerId;

    @Field("amount")
    private double amount;

    @Field("image")
    private String image; // Base64 or receipt proof

    @Field("status")
    private String status = "pending"; // pending, approved, rejected

    @Field("add_amount_to_be_paid")
    private double addAmountToBePaid = 0.0;

    @Field("date")
    private String date;

    public CollectionExpense() {}

    public CollectionExpense(String expenseId, String collectionWorkerId, double amount, String image, String status, double addAmountToBePaid, String date) {
        this.expenseId = expenseId;
        this.collectionWorkerId = collectionWorkerId;
        this.amount = amount;
        this.image = image;
        this.status = status;
        this.addAmountToBePaid = addAmountToBePaid;
        this.date = date;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(String expenseId) {
        this.expenseId = expenseId;
    }

    public String getCollectionWorkerId() {
        return collectionWorkerId;
    }

    public void setCollectionWorkerId(String collectionWorkerId) {
        this.collectionWorkerId = collectionWorkerId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
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

    public double getAddAmountToBePaid() {
        return addAmountToBePaid;
    }

    public void setAddAmountToBePaid(double addAmountToBePaid) {
        this.addAmountToBePaid = addAmountToBePaid;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
