package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DrugMaster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Starter contents of drug_master: real generic drug names with the class the interaction rules use.
 * scheduleType is indicative only (RX = prescription only, OTC = usually over the counter).
 * Clinical content: have a clinician review it before it is relied on.
 */
public final class DrugSeed {

    private DrugSeed() {
    }

    public static List<DrugMaster> entries() {
        List<DrugMaster> all = new ArrayList<>();

        // blood thinners and pain relief
        all.add(drug("Warfarin", "ANTICOAGULANT", "RX"));
        all.add(drug("Aspirin", "SALICYLATE", "OTC", "acetylsalicylic acid", "asa"));
        all.add(drug("Clopidogrel", "ANTIPLATELET", "RX"));
        all.add(drug("Ibuprofen", "NSAID", "OTC"));
        all.add(drug("Diclofenac", "NSAID", "RX"));
        all.add(drug("Naproxen", "NSAID", "RX"));
        all.add(drug("Paracetamol", "ANALGESIC", "OTC", "acetaminophen"));
        all.add(drug("Tramadol", "OPIOID_ANALGESIC", "RX"));

        // anti-infectives
        all.add(drug("Amoxicillin", "PENICILLIN", "RX", "amox"));
        all.add(drug("Amoxicillin-Clavulanate", "PENICILLIN", "RX", "co-amoxiclav", "amoxicillin clavulanate", "amoxicillin/clavulanic acid"));
        all.add(drug("Azithromycin", "MACROLIDE", "RX"));
        all.add(drug("Clarithromycin", "MACROLIDE", "RX"));
        all.add(drug("Ciprofloxacin", "FLUOROQUINOLONE", "RX"));
        all.add(drug("Levofloxacin", "FLUOROQUINOLONE", "RX"));
        all.add(drug("Metronidazole", "NITROIMIDAZOLE", "RX"));
        all.add(drug("Cefixime", "CEPHALOSPORIN", "RX"));
        all.add(drug("Cotrimoxazole", "SULFONAMIDE", "RX", "co-trimoxazole", "sulfamethoxazole-trimethoprim", "trimethoprim-sulfamethoxazole"));
        all.add(drug("Fluconazole", "AZOLE_ANTIFUNGAL", "RX"));

        // heart, blood pressure, cholesterol
        all.add(drug("Amlodipine", "CALCIUM_CHANNEL_BLOCKER", "RX"));
        all.add(drug("Atenolol", "BETA_BLOCKER", "RX"));
        all.add(drug("Metoprolol", "BETA_BLOCKER", "RX"));
        all.add(drug("Propranolol", "BETA_BLOCKER", "RX"));
        all.add(drug("Enalapril", "ACE_INHIBITOR", "RX"));
        all.add(drug("Lisinopril", "ACE_INHIBITOR", "RX"));
        all.add(drug("Losartan", "ARB", "RX"));
        all.add(drug("Hydrochlorothiazide", "THIAZIDE_DIURETIC", "RX", "hctz"));
        all.add(drug("Furosemide", "LOOP_DIURETIC", "RX", "frusemide"));
        all.add(drug("Spironolactone", "POTASSIUM_SPARING_DIURETIC", "RX"));
        all.add(drug("Digoxin", "CARDIAC_GLYCOSIDE", "RX"));
        all.add(drug("Amiodarone", "ANTIARRHYTHMIC", "RX"));
        all.add(drug("Isosorbide Mononitrate", "NITRATE", "RX"));
        all.add(drug("Atorvastatin", "STATIN", "RX"));
        all.add(drug("Simvastatin", "STATIN", "RX"));

        // diabetes, stomach, breathing, allergy, steroids, thyroid
        all.add(drug("Metformin", "BIGUANIDE", "RX"));
        all.add(drug("Insulin", "INSULIN", "RX"));
        all.add(drug("Omeprazole", "PPI", "RX"));
        all.add(drug("Pantoprazole", "PPI", "RX"));
        all.add(drug("Salbutamol", "BRONCHODILATOR", "RX", "albuterol"));
        all.add(drug("Cetirizine", "ANTIHISTAMINE", "OTC"));
        all.add(drug("Prednisolone", "CORTICOSTEROID", "RX"));
        all.add(drug("Levothyroxine", "THYROID_HORMONE", "RX"));

        // mind and nerves, and others with well-known interactions
        all.add(drug("Sertraline", "SSRI", "RX"));
        all.add(drug("Fluoxetine", "SSRI", "RX"));
        all.add(drug("Alprazolam", "BENZODIAZEPINE", "RX"));
        all.add(drug("Diazepam", "BENZODIAZEPINE", "RX"));
        all.add(drug("Lithium", "MOOD_STABILISER", "RX"));
        all.add(drug("Methotrexate", "ANTIMETABOLITE", "RX"));
        all.add(drug("Sildenafil", "PDE5_INHIBITOR", "RX"));

        return all;
    }

    private static DrugMaster drug(String name, String drugClass, String scheduleType, String... aliases) {
        DrugMaster d = new DrugMaster();
        d.setName(name);
        d.setNormalizedName(name.toLowerCase(Locale.ROOT));
        d.setDrugClass(drugClass);
        d.setScheduleType(scheduleType);
        d.setAliases(Arrays.stream(aliases).map(a -> a.toLowerCase(Locale.ROOT)).toList());
        return d;
    }
}
