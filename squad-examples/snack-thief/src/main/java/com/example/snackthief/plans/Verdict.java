package com.example.snackthief.plans;

import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import java.util.List;

/**
 * The final verdict. Justice will be served.
 * (Along with the replacement pizza.)
 */
@SquadPlan(description = "Official snack theft verdict and sentencing")
public class Verdict {
    @Required public String      verdict;        // "GUILTY" / "NOT GUILTY" / "INCONCLUSIVE"
    @Required public String      culprit;        // Name of the accused
    @Required public String      sentence;       // "Buy everyone donuts on Friday"
    public List<String>          evidence;       // Key evidence used
    public String                dissent;        // HRBot's inevitable objection
    public String                dramaticClosing; // JudgeJudy's final words
    public boolean               appealAllowed;  // Always false. JudgeJudy does not do appeals.
}
