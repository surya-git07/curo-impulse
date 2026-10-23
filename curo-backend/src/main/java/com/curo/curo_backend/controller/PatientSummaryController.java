package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import com.curo.curo_backend.model.legacy.DrugMaster;
import com.curo.curo_backend.model.legacy.Prescription;
import com.curo.curo_backend.repository.legacy.DrugMasterRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/patient")
public class PatientSummaryController {

    private static final String SANGAMI_BASE_URL = "https://amr-pvms.onrender.com";
    @Value("${patient-service.api-key}")
    private String apiKey;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    @GetMapping("/{abha}/summary")
    public ResponseEntity<?> getSummary(@PathVariable String abha) {
        String url = SANGAMI_BASE_URL + "/patient/" + abha + "/summary";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Object> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, Object.class
            );
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
        System.out.println("SANGAMI CALL FAILED: " + e.getClass().getName() + " - " + e.getMessage());
        // ... rest stays the same
            // Sangami's service is unreachable or timed out — return a fallback
            // so the UI doesn't hang, and clearly mark it as offline data.
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("abha", abha);
            fallback.put("name", "(Unavailable — patient service offline)");
            fallback.put("allergies", "Unknown");
            fallback.put("conditions", "Unknown");
            fallback.put("offline", true);
            return ResponseEntity.ok(fallback);
        }
    }

    @GetMapping("/{abha}/history")
    public ResponseEntity<?> getHistory(@PathVariable String abha) {
        List<Prescription> history = prescriptionRepository.findByPatientAbhaOrderByIssuedAtDesc(abha);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Prescription p : history) {
            Map<String, Object> item = new HashMap<>();
            item.put("drug", p.getDrug());
            item.put("dose", p.getDose());
            item.put("duration", p.getDuration());
            item.put("issuedAt", p.getIssuedAt());
            item.put("status", p.getStatus());

            Optional<DrugMaster> dm = drugMasterRepository.findByName(p.getDrug());
            String drugClass = dm.map(DrugMaster::getDrugClass).orElse(null);
            item.put("drugClass", drugClass);

            String flag = "NONE";
            if (drugClass != null && p.getIssuedAt() != null) {
                long daysSince = ChronoUnit.DAYS.between(p.getIssuedAt().toLocalDate(), LocalDate.now());
                if (daysSince <= 15) flag = "RECENT_HIGH";
                else if (daysSince <= 30) flag = "RECENT_MEDIUM";
            }
            item.put("riskFlag", flag);

            result.add(item);
        }
        return ResponseEntity.ok(result);
    }
}