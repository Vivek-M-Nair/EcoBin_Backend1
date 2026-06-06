package com.EcoBin.backend.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

// The blueprint for the State database which have district id and name

@Document(collection = "State_dist")
public class StateDist {

    @Id
    private String distId;

    @Field("Dist_name")
    private String distName;

    // Constructors
    public StateDist() {
    }

    public StateDist(String distId, String distName) {
        this.distId = distId;
        this.distName = distName;
    }

    // Getters and Setters
    public String getDistId() {
        return distId;
    }

    public void setDistId(String distId) {
        this.distId = distId;
    }

    public String getDistName() {
        return distName;
    }

    public void setDistName(String distName) {
        this.distName = distName;
    }
}
