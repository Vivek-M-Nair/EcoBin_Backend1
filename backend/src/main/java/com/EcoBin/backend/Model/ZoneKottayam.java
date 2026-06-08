package com.EcoBin.backend.Model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "Zone_Kottayam")
public class ZoneKottayam extends ZoneDetails {
    public ZoneKottayam() {
    }

    public ZoneKottayam(String zoneId, String localBodyName, int firstWard) {
        super(zoneId, localBodyName, firstWard);
    }
}