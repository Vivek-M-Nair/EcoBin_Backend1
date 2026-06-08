package com.EcoBin.backend;

import com.EcoBin.backend.Model.StateDist;
import com.EcoBin.backend.repository.StateDistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private StateDistRepository stateDistRepository;

    // Changes the endpoint to safely FETCH the data instead of inserting it
    // Test URL: http://localhost:8081/api/test/districts
    @GetMapping("/districts")
    public List<StateDist> getAllDistricts() {
        return stateDistRepository.findAll();
    }
}