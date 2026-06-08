package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "feedback_table")
public class Feedback {
    @Id
    private String feedbackId;

    @Field("feedbackdescription")
    private String feedbackDescription;

    @Field("rating")
    private int rating; // rating out of 5

    public Feedback() {}

    public Feedback(String feedbackId, String feedbackDescription, int rating) {
        this.feedbackId = feedbackId;
        this.feedbackDescription = feedbackDescription;
        this.rating = rating;
    }

    public String getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(String feedbackId) {
        this.feedbackId = feedbackId;
    }

    public String getFeedbackDescription() {
        return feedbackDescription;
    }

    public void setFeedbackDescription(String feedbackDescription) {
        this.feedbackDescription = feedbackDescription;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }
}
