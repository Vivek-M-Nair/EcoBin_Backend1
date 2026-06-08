package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "registered_user_collections")
public class RegisteredUserCollection {
    @Id
    private String userId;

    @Field("next_scheduled_date")
    private String nextScheduledDate;

    @Field("last_10_time_waste_collected_date")
    private List<String> last10TimeWasteCollectedDate = new ArrayList<>();

    @Field("last_collection_status")
    private String lastCollectionStatus;

    public RegisteredUserCollection() {}

    public RegisteredUserCollection(String userId, String nextScheduledDate, List<String> last10TimeWasteCollectedDate, String lastCollectionStatus) {
        this.userId = userId;
        this.nextScheduledDate = nextScheduledDate;
        this.last10TimeWasteCollectedDate = last10TimeWasteCollectedDate;
        this.lastCollectionStatus = lastCollectionStatus;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNextScheduledDate() {
        return nextScheduledDate;
    }

    public void setNextScheduledDate(String nextScheduledDate) {
        this.nextScheduledDate = nextScheduledDate;
    }

    public List<String> getLast10TimeWasteCollectedDate() {
        return last10TimeWasteCollectedDate;
    }

    public void setLast10TimeWasteCollectedDate(List<String> last10TimeWasteCollectedDate) {
        this.last10TimeWasteCollectedDate = last10TimeWasteCollectedDate;
    }

    public String getLastCollectionStatus() {
        return lastCollectionStatus;
    }

    public void setLastCollectionStatus(String lastCollectionStatus) {
        this.lastCollectionStatus = lastCollectionStatus;
    }
}
