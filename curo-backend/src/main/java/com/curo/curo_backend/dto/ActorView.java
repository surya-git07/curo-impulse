package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActorView {
    private String userId;
    private String role;
    private String name;
    private String facilityName;
}
