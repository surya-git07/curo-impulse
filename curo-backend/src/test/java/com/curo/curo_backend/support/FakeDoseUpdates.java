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

    @Override
    public boolean markTaken(String doseId, Instant takenAt, boolean late, String takenByUserId) {
        for (DoseLog d : doses) {
            if (doseId.equals(d.getId()) && d.getStatus() != DoseStatus.TAKEN) {
                d.setStatus(DoseStatus.TAKEN);
                d.setTakenAt(takenAt);
                d.setLate(late);
                d.setTakenByUserId(takenByUserId);
                return true;
            }
        }
        return false;
    }

    @Override
    public long markMissed(Instant cutoff) {
        long changed = 0;
        for (DoseLog d : doses) {
            if (d.getStatus() == DoseStatus.UPCOMING && !d.getScheduledAt().isAfter(cutoff)) {
                d.setStatus(DoseStatus.MISSED);
                changed++;
            }
        }
        return changed;
    }
}
