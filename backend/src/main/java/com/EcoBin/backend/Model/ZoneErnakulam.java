package com.EcoBin.backend.Model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "Zone_Ernakulam")
public class ZoneErnakulam extends ZoneDetails {
    public ZoneErnakulam() {
    }

    public ZoneErnakulam(String zoneId, String localBodyName, int firstWard) {
        super(zoneId, localBodyName, firstWard);
    }
}