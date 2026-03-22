package com.example.snackthief.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/**
 * Detailed suspect profile. CSI would be proud.
 */
@SquadPlan(description = "Suspect profile for snack theft investigation")
public class SuspectProfile {
    @Required public String      name;
    @Required public String      department;
    public String                motive;         // "Was hungry, claimed fridge was 'communal'"
    public String                alibi;          // "Claims they were 'in a meeting'"
    public List<String>          priorOffences;  // Past snack crimes on record
    public String               guiltScore;  // "0.0" to "1.0"     // 0.0 to 1.0
    public String                knownFoodPreferences; // "Loves pizza. LOVES it."
    public boolean               hasAccessToFridge;
    public String                suspicionReason;
}
