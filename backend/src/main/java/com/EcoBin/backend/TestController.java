package com.EcoBin.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;

// Notice the capital 'M' in Model and lowercase 'r' in repository to match your folders!
// import com.EcoBin.backend.Model.HouseDetails;
import com.EcoBin.backend.Model.StateDist;
// import com.EcoBin.backend.repository.HouseDetailsRepository;
import com.EcoBin.backend.repository.StateDistRepository;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private StateDistRepository stateDistRepository;

    @GetMapping("/create-districts")
    public String createDistrictData() {
        // Creating the objects for Kottayam and Ernakulam
        StateDist kottayam = new StateDist("Dist-05", "Kottayam");
        StateDist ernakulam = new StateDist("Dist-06", "Ernakulam");

        // Saving both items at once using saveAll()
        stateDistRepository.saveAll(Arrays.asList(kottayam, ernakulam));

        return "District data for Kottayam and Ernakulam saved successfully!";
    }
}
