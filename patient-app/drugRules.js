// drugRules.js
// Static lookup tables for the Home interaction and allergy checks. No backend call.
//
// Ported from the backend on origin/main (commit ed63709) so the app and server
// agree: curo-backend/.../service/InteractionTable.java, AllergyTable.java and
// DrugSeed.java. (There is no drug-rules.json in the repo; these Java tables are
// the source.) If the backend tables change, update this file to match.
// Clinical content: have a clinician review it before it is relied on.

// name, drug class (the token the rules use), aliases. Names are matched lower case.
export const DRUGS = [
  // blood thinners and pain relief
  ['Warfarin', 'ANTICOAGULANT'],
  ['Aspirin', 'SALICYLATE', 'acetylsalicylic acid', 'asa'],
  ['Clopidogrel', 'ANTIPLATELET'],
  ['Ibuprofen', 'NSAID'],
  ['Diclofenac', 'NSAID'],
  ['Naproxen', 'NSAID'],
  ['Paracetamol', 'ANALGESIC', 'acetaminophen'],
  ['Tramadol', 'OPIOID_ANALGESIC'],

  // anti-infectives
  ['Amoxicillin', 'PENICILLIN', 'amox'],
  ['Amoxicillin-Clavulanate', 'PENICILLIN', 'co-amoxiclav', 'amoxicillin clavulanate', 'amoxicillin/clavulanic acid'],
  ['Azithromycin', 'MACROLIDE'],
  ['Clarithromycin', 'MACROLIDE'],
  ['Ciprofloxacin', 'FLUOROQUINOLONE'],
  ['Levofloxacin', 'FLUOROQUINOLONE'],
  ['Metronidazole', 'NITROIMIDAZOLE'],
  ['Cefixime', 'CEPHALOSPORIN'],
  ['Cotrimoxazole', 'SULFONAMIDE', 'co-trimoxazole', 'sulfamethoxazole-trimethoprim', 'trimethoprim-sulfamethoxazole'],
  ['Fluconazole', 'AZOLE_ANTIFUNGAL'],

  // heart, blood pressure, cholesterol
  ['Amlodipine', 'CALCIUM_CHANNEL_BLOCKER'],
  ['Atenolol', 'BETA_BLOCKER'],
  ['Metoprolol', 'BETA_BLOCKER'],
  ['Propranolol', 'BETA_BLOCKER'],
  ['Enalapril', 'ACE_INHIBITOR'],
  ['Lisinopril', 'ACE_INHIBITOR'],
  ['Losartan', 'ARB'],
  ['Hydrochlorothiazide', 'THIAZIDE_DIURETIC', 'hctz'],
  ['Furosemide', 'LOOP_DIURETIC', 'frusemide'],
  ['Spironolactone', 'POTASSIUM_SPARING_DIURETIC'],
  ['Digoxin', 'CARDIAC_GLYCOSIDE'],
  ['Amiodarone', 'ANTIARRHYTHMIC'],
  ['Isosorbide Mononitrate', 'NITRATE'],
  ['Atorvastatin', 'STATIN'],
  ['Simvastatin', 'STATIN'],

  // diabetes, stomach, breathing, allergy, steroids, thyroid
  ['Metformin', 'BIGUANIDE'],
  ['Insulin', 'INSULIN'],
  ['Omeprazole', 'PPI'],
  ['Pantoprazole', 'PPI'],
  ['Salbutamol', 'BRONCHODILATOR', 'albuterol'],
  ['Cetirizine', 'ANTIHISTAMINE'],
  ['Prednisolone', 'CORTICOSTEROID'],
  ['Levothyroxine', 'THYROID_HORMONE'],

  // mind and nerves, and others with well-known interactions
  ['Sertraline', 'SSRI'],
  ['Fluoxetine', 'SSRI'],
  ['Alprazolam', 'BENZODIAZEPINE'],
  ['Diazepam', 'BENZODIAZEPINE'],
  ['Lithium', 'MOOD_STABILISER'],
  ['Methotrexate', 'ANTIMETABOLITE'],
  ['Sildenafil', 'PDE5_INHIBITOR'],
];

// Each side (a, b) is a lower-case drug name or drug class; a rule applies in either order.
// severity: HIGH | MODERATE | LOW  (shown as major | moderate | minor)
export const INTERACTION_RULES = [
  // bleeding
  { a: 'warfarin', b: 'aspirin', severity: 'HIGH', warning: 'Serious bleeding risk.',
    explanation: 'Aspirin adds an antiplatelet effect to warfarin and can irritate the stomach lining, so the risk of serious bleeding rises sharply. Use together only if a clinician has decided the benefit outweighs the risk, with close monitoring.' },
  { a: 'warfarin', b: 'nsaid', severity: 'HIGH', warning: 'Serious bleeding risk.',
    explanation: "NSAIDs can cause stomach bleeding and interfere with clotting, which adds to warfarin's effect. Avoid the combination where possible." },
  { a: 'warfarin', b: 'clarithromycin', severity: 'HIGH', warning: 'Warfarin effect may rise sharply.',
    explanation: 'Clarithromycin can raise INR and the risk of bleeding. If both are needed, check INR closely.' },
  { a: 'warfarin', b: 'metronidazole', severity: 'HIGH', warning: 'Warfarin effect may rise sharply.',
    explanation: 'Metronidazole can markedly raise INR and the risk of bleeding. If both are needed, check INR closely.' },
  { a: 'warfarin', b: 'fluconazole', severity: 'HIGH', warning: 'Warfarin effect may rise sharply.',
    explanation: 'Fluconazole slows the breakdown of warfarin and can raise INR and the risk of bleeding.' },
  { a: 'warfarin', b: 'fluoroquinolone', severity: 'MODERATE', warning: 'Warfarin effect may rise.',
    explanation: 'Fluoroquinolone antibiotics can raise INR. Monitor INR during and after the course.' },
  { a: 'warfarin', b: 'paracetamol', severity: 'LOW', warning: 'Regular high-dose paracetamol may raise INR.',
    explanation: "Occasional doses are generally fine; sustained daily use can increase warfarin's effect." },
  { a: 'clopidogrel', b: 'aspirin', severity: 'MODERATE', warning: 'Higher bleeding risk.',
    explanation: 'Two antiplatelet drugs together increase bleeding risk. This pairing is often intentional after a heart event or stent, so confirm it is planned.' },
  { a: 'clopidogrel', b: 'omeprazole', severity: 'MODERATE', warning: 'Clopidogrel may work less well.',
    explanation: 'Omeprazole can reduce the activation of clopidogrel. If a stomach-protecting drug is needed, a clinician may prefer another option.' },
  { a: 'aspirin', b: 'nsaid', severity: 'MODERATE', warning: 'Higher stomach-bleeding risk; aspirin may work less well.',
    explanation: "Some NSAIDs such as ibuprofen can blunt aspirin's heart-protecting effect, and both irritate the stomach lining." },
  { a: 'ssri', b: 'aspirin', severity: 'MODERATE', warning: 'Higher bleeding risk.',
    explanation: 'SSRIs affect platelets, so with aspirin the risk of bleeding, especially in the stomach, goes up.' },
  { a: 'ssri', b: 'nsaid', severity: 'MODERATE', warning: 'Higher bleeding risk.',
    explanation: 'SSRIs together with NSAIDs raise the risk of stomach bleeding.' },
  { a: 'prednisolone', b: 'nsaid', severity: 'MODERATE', warning: 'Higher risk of stomach ulcers and bleeding.',
    explanation: 'Corticosteroids and NSAIDs together increase the risk of ulcers and gastrointestinal bleeding.' },

  // muscle, heart rhythm, blood pressure, potassium
  { a: 'simvastatin', b: 'clarithromycin', severity: 'HIGH', warning: 'Risk of serious muscle damage.',
    explanation: 'Clarithromycin raises simvastatin levels and the risk of rhabdomyolysis. Simvastatin is usually paused during the course.' },
  { a: 'atorvastatin', b: 'clarithromycin', severity: 'MODERATE', warning: 'Risk of muscle damage.',
    explanation: 'Clarithromycin raises atorvastatin levels. Watch for muscle pain or weakness, or consider pausing the statin.' },
  { a: 'simvastatin', b: 'amlodipine', severity: 'MODERATE', warning: 'Simvastatin dose may need limiting.',
    explanation: 'Amlodipine raises simvastatin levels. The simvastatin dose is usually capped when used together.' },
  { a: 'digoxin', b: 'amiodarone', severity: 'HIGH', warning: 'Digoxin level may rise to toxic levels.',
    explanation: 'Amiodarone raises digoxin levels. The digoxin dose usually needs to be reduced and levels checked.' },
  { a: 'digoxin', b: 'clarithromycin', severity: 'HIGH', warning: 'Digoxin level may rise to toxic levels.',
    explanation: 'Clarithromycin can raise digoxin levels and cause toxicity. Monitor closely or choose another antibiotic.' },
  { a: 'ace_inhibitor', b: 'potassium_sparing_diuretic', severity: 'MODERATE', warning: 'Potassium may rise too high.',
    explanation: 'ACE inhibitors and potassium-sparing diuretics both raise potassium. Check potassium and kidney function.' },
  { a: 'arb', b: 'potassium_sparing_diuretic', severity: 'MODERATE', warning: 'Potassium may rise too high.',
    explanation: 'ARBs and potassium-sparing diuretics both raise potassium. Check potassium and kidney function.' },
  { a: 'ace_inhibitor', b: 'nsaid', severity: 'MODERATE', warning: 'Blood pressure drug may work less well; kidney risk.',
    explanation: 'NSAIDs can blunt ACE inhibitors and, especially with a diuretic, can harm the kidneys.' },
  { a: 'arb', b: 'nsaid', severity: 'MODERATE', warning: 'Blood pressure drug may work less well; kidney risk.',
    explanation: 'NSAIDs can blunt ARBs and, especially with a diuretic, can harm the kidneys.' },
  { a: 'sildenafil', b: 'nitrate', severity: 'HIGH', warning: 'Severe drop in blood pressure.',
    explanation: 'Sildenafil with a nitrate can cause a dangerous fall in blood pressure. Do not combine.' },

  // brain and nerves
  { a: 'tramadol', b: 'ssri', severity: 'HIGH', warning: 'Risk of serotonin syndrome and seizures.',
    explanation: 'Tramadol with an SSRI can cause serotonin syndrome (agitation, fast heartbeat, fever, tremor) and lowers the seizure threshold.' },
  { a: 'benzodiazepine', b: 'opioid_analgesic', severity: 'HIGH', warning: 'Risk of dangerously slow breathing.',
    explanation: 'Benzodiazepines with opioid pain relievers such as tramadol can cause profound drowsiness, slowed breathing and death.' },
  { a: 'lithium', b: 'nsaid', severity: 'HIGH', warning: 'Lithium may reach toxic levels.',
    explanation: 'NSAIDs reduce lithium clearance and can raise lithium to toxic levels. Check levels if the combination cannot be avoided.' },
  { a: 'lithium', b: 'ace_inhibitor', severity: 'MODERATE', warning: 'Lithium level may rise.',
    explanation: 'ACE inhibitors can raise lithium levels. Monitor lithium.' },
  { a: 'lithium', b: 'thiazide_diuretic', severity: 'MODERATE', warning: 'Lithium level may rise.',
    explanation: 'Thiazide diuretics reduce lithium clearance. Monitor lithium.' },

  // others
  { a: 'methotrexate', b: 'nsaid', severity: 'HIGH', warning: 'Methotrexate toxicity risk.',
    explanation: 'NSAIDs can slow methotrexate clearance and raise it to toxic levels.' },
  { a: 'methotrexate', b: 'cotrimoxazole', severity: 'HIGH', warning: 'Serious blood-count suppression.',
    explanation: 'Cotrimoxazole with methotrexate can cause severe bone-marrow suppression. Avoid.' },
  { a: 'methotrexate', b: 'penicillin', severity: 'MODERATE', warning: 'Methotrexate level may rise.',
    explanation: 'Penicillins can reduce methotrexate clearance. Monitor for toxicity.' },
  { a: 'insulin', b: 'beta_blocker', severity: 'MODERATE', warning: 'May hide the warning signs of low blood sugar.',
    explanation: 'Beta blockers can mask the shakiness and fast heartbeat that warn of hypoglycaemia, and can prolong it.' },
];

// Common ways of writing an allergen, mapped to the token the rules use.
export const ALLERGY_SYNONYMS = {
  penicillins: 'penicillin',
  penicilin: 'penicillin',
  sulfa: 'sulfonamide',
  sulpha: 'sulfonamide',
  'sulfa drugs': 'sulfonamide',
  'sulpha drugs': 'sulfonamide',
  sulphonamide: 'sulfonamide',
  sulphonamides: 'sulfonamide',
  sulfonamides: 'sulfonamide',
  nsaids: 'nsaid',
  'anti-inflammatories': 'nsaid',
  cephalosporins: 'cephalosporin',
  macrolides: 'macrolide',
  fluoroquinolones: 'fluoroquinolone',
  quinolones: 'fluoroquinolone',
  statins: 'statin',
  ssris: 'ssri',
  'ace inhibitors': 'ace_inhibitor',
  'beta blockers': 'beta_blocker',
  opioids: 'opioid_analgesic',
};

// Allergic to `allergen` (a drug or class token) -> may react to a drug carrying `drugToken`.
export const CROSS_REACTIONS = [
  { allergen: 'penicillin', drugToken: 'cephalosporin', severity: 'MODERATE', warning: 'Possible cross-reaction with a penicillin allergy.',
    explanation: 'A small share of people allergic to penicillin also react to cephalosporins. Confirm the type of reaction before use.' },
  { allergen: 'aspirin', drugToken: 'nsaid', severity: 'MODERATE', warning: 'Possible cross-reaction with an aspirin allergy.',
    explanation: 'People who react to aspirin can react to other NSAIDs.' },
  { allergen: 'nsaid', drugToken: 'aspirin', severity: 'HIGH', warning: 'Aspirin is an NSAID.',
    explanation: 'Aspirin belongs to the same family of drugs as other NSAIDs and commonly cross-reacts.' },
  { allergen: 'salicylate', drugToken: 'nsaid', severity: 'MODERATE', warning: 'Possible cross-reaction with a salicylate allergy.',
    explanation: 'People who react to salicylates can react to NSAIDs.' },
];
