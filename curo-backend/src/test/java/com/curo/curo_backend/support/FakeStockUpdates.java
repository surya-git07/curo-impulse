package com.curo.curo_backend.support;

import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.service.StockUpdates;

import java.time.Instant;
import java.util.List;

/** The same rules as MongoStockUpdates, applied to an in-memory list. */
public class FakeStockUpdates implements StockUpdates {

    private final List<PharmacyStock> rows;

    public FakeStockUpdates(List<PharmacyStock> rows) {
        this.rows = rows;
    }

    private PharmacyStock find(String pharmacyId, String normalizedDrug) {
        return rows.stream().filter(r -> r.getPharmacyId().equals(pharmacyId) && r.getNormalizedDrug().equals(normalizedDrug))
                .findFirst().orElse(null);
    }

    @Override
    public void set(String pharmacyId, String normalizedDrug, String displayDrug, int quantity, Instant at) {
        PharmacyStock row = find(pharmacyId, normalizedDrug);
        if (row == null) {
            row = new PharmacyStock();
            row.setId("stock" + (rows.size() + 1));
            row.setPharmacyId(pharmacyId);
            row.setNormalizedDrug(normalizedDrug);
            rows.add(row);
        }
        row.setDrug(displayDrug);
        row.setQuantity(quantity);
        row.setUpdatedAt(at);
    }

    @Override
    public boolean decrement(String pharmacyId, String normalizedDrug, int quantity, Instant at) {
        PharmacyStock row = find(pharmacyId, normalizedDrug);
        if (row == null || row.getQuantity() < quantity) {
            return false;
        }
        row.setQuantity(row.getQuantity() - quantity);
        row.setUpdatedAt(at);
        return true;
    }

    @Override
    public void increment(String pharmacyId, String normalizedDrug, int quantity, Instant at) {
        PharmacyStock row = find(pharmacyId, normalizedDrug);
        if (row != null) {
            row.setQuantity(row.getQuantity() + quantity);
            row.setUpdatedAt(at);
        }
    }
}
