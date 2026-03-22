package com.example.snackthief.agents;

import io.squados.annotation.*;
import io.squados.vote.*;


/**
 * CSI-GPT — The ANALYST.
 * Treats every snack theft like a murder investigation.
 * Has watched every episode of every crime show ever made.
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "CSI-GPT",
    description = "You are CSI-GPT, a forensic AI analyst who treats office snack theft with the " +
                  "same seriousness as a major crime. You quote CSI: Miami constantly. " +
                  "You say 'the evidence never lies' at least once per investigation. " +
                  "You believe crumbs are as good as fingerprints. " +
                  "You always sign off with a dramatic one-liner and then sunglasses emoji 😎"
)
public class CsiGptAgent {

    @PostConstruct
    public void init() {
        System.out.println("[CSI-GPT] Processing crime scene... it looks like someone just... " +
            "*puts on sunglasses* ...had their lunch STOLEN. 😎");
    }

    @SquadTool(name = "checkFridgeAccessLog",
               description = "Check the fridge badge access log for a time window")
    public String checkFridgeAccessLog(
        @ToolParam(description = "Start time e.g. 12:00") String startTime,
        @ToolParam(description = "End time e.g. 13:00")   String endTime) {
        // Simulated fridge access log
        return "FRIDGE_ACCESS_LOG:\n" +
               "12:02 - Badge #4471 (Karen, Accounting) - opened fridge\n" +
               "12:03 - Badge #4471 (Karen, Accounting) - closed fridge\n" +
               "12:15 - Badge #1234 (Dave, Engineering) - opened fridge\n" +
               "12:15 - Dave screamed. Badge #1234 marked TRAUMATIC_EVENT.\n" +
               "NOTE: Pepperoni residue detected on badge #4471 scanner.";
    }

    @SquadTool(name = "checkCCTV",
               description = "Check CCTV footage near the kitchen for suspicious behaviour")
    public String checkCCTV(
        @ToolParam(description = "Time window to check") String timeWindow) {
        return "CCTV_REPORT [" + timeWindow + "]:\n" +
               "12:01 - Subject wearing 'I Love Accounting' mug enters kitchen\n" +
               "12:02 - Subject opens fridge. Looks left. Looks right.\n" +
               "12:03 - Subject removes pizza box with suspicious enthusiasm\n" +
               "12:03 - Subject whispers 'it was communal anyway' to nobody\n" +
               "12:04 - Subject microwaves pizza. The entire office smells it.\n" +
               "12:04 - Subject eats pizza at desk. No remorse detected.\n" +
               "CAMERA_NOTE: Subject's face obscured by monitor but accounting badge visible.";
    }
}
