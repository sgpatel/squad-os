package com.example.snackthief.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/**
 * Typed crime report — replaces "here is what I think happened..." raw text.
 * Now we get REAL data. This is serious police work.
 */
@SquadPlan(description = "Official snack crime incident report")
public class CrimeReport {
    @Required public String    crimeType;         // "Pizza Theft", "Yogurt Disappearance"
    @Required public String    victim;             // "Dave from Engineering"
    @Required public String    itemStolen;         // "3 slices of leftover pepperoni pizza"
    @Required public String    estimatedTimeOfCrime; // "12:00pm - 12:15pm"
    public List<String>        witnesses;          // ["The fridge itself", "Empty feeling in Dave's heart"]
    public List<String>        evidence;           // ["Crumbs on keyboard", "Suspicious cheese breath"]
    public String              suspiciousBehaviour; // "Someone microwaved fish the same day"
    public String              severity;           // "CRITICAL" / "HIGH" / "MEDIUM" / "LOW"
}
