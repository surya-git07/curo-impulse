package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import com.curo.curo_backend.service.RiskScoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/ai")
public class AiRiskController {

    @Autowired
    private RiskScoringService riskScoringService;

    private static final String SANGAMI_BASE_URL = "https://amr-pvms.onrender.com";
    @Value("${patient-service.api-key}")
    private String apiKey;

    @PostMapping("/risk-score")
    public ResponseEntity<?> getRiskScore(@RequestBody Map<String, Object> request) {
        String drug = String.valueOf(request.getOrDefault("drug", ""));
        String patientAbha = String.valueOf(request.getOrDefault("patientAbha", ""));
        String allergies = String.valueOf(request.getOrDefault("allergies", "None"));
        Integer durationDays = parseDurationDays(request.get("duration"));

        List<String> explanations = new ArrayList<>();

        // 1. Local repeat-prescription check (based on this patient's own history)
        String localRisk = "UNKNOWN";
        if (!patientAbha.isBlank()) {
            localRisk = riskScoringService.calculateRisk(patientAbha, drug);
            if ("HIGH".equals(localRisk)) {
                explanations.add("Same drug class was prescribed to this patient within the last 15 days.");
            } else if ("MEDIUM".equals(localRisk)) {
                explanations.add("Same drug class was prescribed to this patient within the last 30 days.");
            }
        }

        // 2. Sangami's WHO AWaRe + allergy cross-check
        boolean blocked = false;
        String awareCategory = null;
        String remoteRisk = "UNKNOWN";
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("drug_name", drug);
            body.put("duration_days", durationDays);
            body.put("diagnosis_match", 1); // no diagnosis field in UI yet — assumed match
            body.put("patient_allergies", allergies);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SANGAMI_BASE_URL + "/risk/score", entity, Map.class
            );

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                blocked = Boolean.TRUE.equals(resBody.get("blocked"));
                awareCategory = String.valueOf(resBody.get("aware_category"));
                boolean riskFlag = Boolean.TRUE.equals(resBody.get("risk_flag"));
                remoteRisk = riskFlag ? "HIGH" : "LOW";

                Object explanationObj = resBody.get("explanation");
                if (explanationObj instanceof List<?> list) {
                    for (Object e : list) explanations.add(String.valueOf(e));
                }
            }
        } catch (Exception e) {
            explanations.add("Could not reach WHO AWaRe risk service — showing local history-based risk only.");
        }

        // 3. Merge
        String overallRisk;
        if (blocked) {
            overallRisk = "BLOCKED";
        } else {
            overallRisk = mostSevere(localRisk, remoteRisk);
        }

        double riskScoreNum = switch (overallRisk) {
            case "BLOCKED" -> 1.0;
            case "HIGH" -> 0.75;
            case "MEDIUM" -> 0.45;
            case "LOW" -> 0.15;
            default -> 0.0;
        };

        Map<String, Object> result = new HashMap<>();
        result.put("riskLevel", overallRisk);
        result.put("riskScore", riskScoreNum);
        result.put("blocked", blocked);
        result.put("awareCategory", awareCategory);
        result.put("explanation", String.join(" ", explanations));

        return ResponseEntity.ok(result);
    }

    private String mostSevere(String a, String b) {
        List<String> order = List.of("UNKNOWN", "LOW", "MEDIUM", "HIGH");
        int ai = order.indexOf(a);
        int bi = order.indexOf(b);
        return order.get(Math.max(Math.max(ai, 0), Math.max(bi, 0)));
    }

    private Integer parseDurationDays(Object durationObj) {
        if (durationObj == null) return 5;
        String s = String.valueOf(durationObj);
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 5;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 5;
        }
    }
}