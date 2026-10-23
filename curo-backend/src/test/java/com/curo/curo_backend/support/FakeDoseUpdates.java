package com.curo.curo_backend.support;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.service.DoseUpdates;

import java.time.Instant;
import java.util.List;

/** The same rules as MongoDoseUpdates, applied to an in-memory list. */
public class FakeDoseUpdates implements DoseUpdates {

    private final List<DoseLog> doses;

    public FakeDoseUpdates(List<DoseLog> doses) {
        this.doses = doses;
    }

    private DoseLog find(String id) {
        return doses.stream().filter(d -> id.equals(d.getId())).findFirst().orElse(null);
    }

    @Override
    public boolean markTaken(String doseId, Instant takenAt, boolean late, String takenByUserId) {
        DoseLog d = find(doseId);
        if (d == null || d.getStatus() == DoseStatus.TAKEN) {
            return false;
        }
        d.setStatus(DoseStatus.TAKEN);
        d.setTakenAt(takenAt);
        d.setLate(late);
        d.setTakenByUserId(takenByUserId);
        return true;
    }

    @Override
    public boolean markMissed(String doseId) {
        DoseLog d = find(doseId);
        if (d == null || d.getStatus() != DoseStatus.UPCOMING) {
            return false;
        }
        d.setStatus(DoseStatus.MISSED);
        return true;
    }

    @Override
    public boolean markReminderSent(String doseId, Instant at) {
        DoseLog d = find(doseId);
        if (d == null || d.getReminderSentAt() != null) {
            return false;
        }
        d.setReminderSentAt(at);
        return true;
    }
}
