package com.curo.curo_backend.service;

import com.curo.curo_backend.model.Severity;

import java.util.List;

/**
 * Hardcoded table of well-known dangerous drug pairs. It is the fallback for the interaction check (and the only
 * source until an AI check is plugged in behind InteractionChecker). Each side is a lower-case drug name or a drug
 * class from drug_master; a rule applies in either order. Clinical content: have a clinician review it.
 */
public final class InteractionTable {

    public record Rule(String a, String b, Severity severity, String warning, String explanation) {
    }

    private InteractionTable() {
    }

    private static final List<Rule> RULES = List.of(
            // bleeding
            new Rule("warfarin", "aspirin", Severity.HIGH, "Serious bleeding risk.",
                    "Aspirin adds an antiplatelet effect to warfarin and can irritate the stomach lining, so the risk of serious bleeding rises sharply. Use together only if a clinician has decided the benefit outweighs the risk, with close monitoring."),
            new Rule("warfarin", "nsaid", Severity.HIGH, "Serious bleeding risk.",
                    "NSAIDs can cause stomach bleeding and interfere with clotting, which adds to warfarin's effect. Avoid the combination where possible."),
            new Rule("warfarin", "clarithromycin", Severity.HIGH, "Warfarin effect may rise sharply.",
                    "Clarithromycin can raise INR and the risk of bleeding. If both are needed, check INR closely."),
            new Rule("warfarin", "metronidazole", Severity.HIGH, "Warfarin effect may rise sharply.",
                    "Metronidazole can markedly raise INR and the risk of bleeding. If both are needed, check INR closely."),
            new Rule("warfarin", "fluconazole", Severity.HIGH, "Warfarin effect may rise sharply.",
                    "Fluconazole slows the breakdown of warfarin and can raise INR and the risk of bleeding."),
            new Rule("warfarin", "fluoroquinolone", Severity.MODERATE, "Warfarin effect may rise.",
                    "Fluoroquinolone antibiotics can raise INR. Monitor INR during and after the course."),
            new Rule("warfarin", "paracetamol", Severity.LOW, "Regular high-dose paracetamol may raise INR.",
                    "Occasional doses are generally fine; sustained daily use can increase warfarin's effect."),
            new Rule("clopidogrel", "aspirin", Severity.MODERATE, "Higher bleeding risk.",
                    "Two antiplatelet drugs together increase bleeding risk. This pairing is often intentional after a heart event or stent, so confirm it is planned."),
            new Rule("clopidogrel", "omeprazole", Severity.MODERATE, "Clopidogrel may work less well.",
                    "Omeprazole can reduce the activation of clopidogrel. If a stomach-protecting drug is needed, a clinician may prefer another option."),
            new Rule("aspirin", "nsaid", Severity.MODERATE, "Higher stomach-bleeding risk; aspirin may work less well.",
                    "Some NSAIDs such as ibuprofen can blunt aspirin's heart-protecting effect, and both irritate the stomach lining."),
            new Rule("ssri", "aspirin", Severity.MODERATE, "Higher bleeding risk.",
                    "SSRIs affect platelets, so with aspirin the risk of bleeding, especially in the stomach, goes up."),
            new Rule("ssri", "nsaid", Severity.MODERATE, "Higher bleeding risk.",
                    "SSRIs together with NSAIDs raise the risk of stomach bleeding."),
            new Rule("prednisolone", "nsaid", Severity.MODERATE, "Higher risk of stomach ulcers and bleeding.",
                    "Corticosteroids and NSAIDs together increase the risk of ulcers and gastrointestinal bleeding."),

            // muscle, heart rhythm, blood pressure, potassium
            new Rule("simvastatin", "clarithromycin", Severity.HIGH, "Risk of serious muscle damage.",
                    "Clarithromycin raises simvastatin levels and the risk of rhabdomyolysis. Simvastatin is usually paused during the course."),
            new Rule("atorvastatin", "clarithromycin", Severity.MODERATE, "Risk of muscle damage.",
                    "Clarithromycin raises atorvastatin levels. Watch for muscle pain or weakness, or consider pausing the statin."),
            new Rule("simvastatin", "amlodipine", Severity.MODERATE, "Simvastatin dose may need limiting.",
                    "Amlodipine raises simvastatin levels. The simvastatin dose is usually capped when used together."),
            new Rule("digoxin", "amiodarone", Severity.HIGH, "Digoxin level may rise to toxic levels.",
                    "Amiodarone raises digoxin levels. The digoxin dose usually needs to be reduced and levels checked."),
            new Rule("digoxin", "clarithromycin", Severity.HIGH, "Digoxin level may rise to toxic levels.",
                    "Clarithromycin can raise digoxin levels and cause toxicity. Monitor closely or choose another antibiotic."),
            new Rule("ace_inhibitor", "potassium_sparing_diuretic", Severity.MODERATE, "Potassium may rise too high.",
                    "ACE inhibitors and potassium-sparing diuretics both raise potassium. Check potassium and kidney function."),
            new Rule("arb", "potassium_sparing_diuretic", Severity.MODERATE, "Potassium may rise too high.",
                    "ARBs and potassium-sparing diuretics both raise potassium. Check potassium and kidney function."),
            new Rule("ace_inhibitor", "nsaid", Severity.MODERATE, "Blood pressure drug may work less well; kidney risk.",
                    "NSAIDs can blunt ACE inhibitors and, especially with a diuretic, can harm the kidneys."),
            new Rule("arb", "nsaid", Severity.MODERATE, "Blood pressure drug may work less well; kidney risk.",
                    "NSAIDs can blunt ARBs and, especially with a diuretic, can harm the kidneys."),
            new Rule("sildenafil", "nitrate", Severity.HIGH, "Severe drop in blood pressure.",
                    "Sildenafil with a nitrate can cause a dangerous fall in blood pressure. Do not combine."),

            // brain and nerves
            new Rule("tramadol", "ssri", Severity.HIGH, "Risk of serotonin syndrome and seizures.",
                    "Tramadol with an SSRI can cause serotonin syndrome (agitation, fast heartbeat, fever, tremor) and lowers the seizure threshold."),
            new Rule("benzodiazepine", "opioid_analgesic", Severity.HIGH, "Risk of dangerously slow breathing.",
                    "Benzodiazepines with opioid pain relievers such as tramadol can cause profound drowsiness, slowed breathing and death."),
            new Rule("lithium", "nsaid", Severity.HIGH, "Lithium may reach toxic levels.",
                    "NSAIDs reduce lithium clearance and can raise lithium to toxic levels. Check levels if the combination cannot be avoided."),
            new Rule("lithium", "ace_inhibitor", Severity.MODERATE, "Lithium level may rise.",
                    "ACE inhibitors can raise lithium levels. Monitor lithium."),
            new Rule("lithium", "thiazide_diuretic", Severity.MODERATE, "Lithium level may rise.",
                    "Thiazide diuretics reduce lithium clearance. Monitor lithium."),

            // others
            new Rule("methotrexate", "nsaid", Severity.HIGH, "Methotrexate toxicity risk.",
                    "NSAIDs can slow methotrexate clearance and raise it to toxic levels."),
            new Rule("methotrexate", "cotrimoxazole", Severity.HIGH, "Serious blood-count suppression.",
                    "Cotrimoxazole with methotrexate can cause severe bone-marrow suppression. Avoid."),
            new Rule("methotrexate", "penicillin", Severity.MODERATE, "Methotrexate level may rise.",
                    "Penicillins can reduce methotrexate clearance. Monitor for toxicity."),
            new Rule("insulin", "beta_blocker", Severity.MODERATE, "May hide the warning signs of low blood sugar.",
                    "Beta blockers can mask the shakiness and fast heartbeat that warn of hypoglycaemia, and can prolong it.")
    );

    public static List<Rule> rules() {
        return RULES;
    }
}
