package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/** Fills drug_master on startup. Adds missing drugs only, never overwrites, and never stops the app from starting. */
@Component
@Order(10)
public class DrugSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DrugSeeder.class);

    @Value("${drugs.seed-on-startup:true}")
    private boolean seedOnStartup;

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            return;
        }
        int added = 0;
        try {
            for (DrugMaster drug : DrugSeed.entries()) {
                if (!drugMasterRepository.existsByNormalizedName(drug.getNormalizedName())) {
                    drugMasterRepository.save(drug);
                    added++;
                }
            }
        } catch (RuntimeException e) {
            // exception type only: driver messages can contain connection details
            log.warn("drug_master seed skipped after {} drugs: MongoDB unreachable or failing ({}). "
                    + "The built-in drug list still drives the interaction checks, but GET /drugs/list will be empty until it is seeded.",
                    added, e.getClass().getSimpleName());
            return;
        }
        log.info("drug_master seed: {} added, {} already present", added, DrugSeed.entries().size() - added);
    }
}
