package com.EcoBin.backend.controller;

import com.EcoBin.backend.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for account deletion (user or collection worker).
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired private AccountService accountService;

    // ===========================
    // DELETE USER ACCOUNT
    // ===========================

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteUserAccount(@PathVariable String userId) {
        Map<String, Object> result = accountService.deleteUserAccount(userId);
        return ResponseEntity.ok(result);
    }

    // ===========================
    // DELETE WORKER ACCOUNT
    // ===========================

    @DeleteMapping("/worker/{workerId}")
    public ResponseEntity<?> deleteWorkerAccount(@PathVariable String workerId) {
        Map<String, Object> result = accountService.deleteWorkerAccount(workerId);
        return ResponseEntity.ok(result);
    }
}
