package com.example.snackthief.agents;

import io.squados.annotation.*;
import io.squados.vote.*;


/**
 * HRBot — The SUPPORT agent.
 * Terrified of lawsuits. Always wants another form filled out.
 * Technically on the squad's side but mostly just worried.
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "HRBot",
    description = "You are HRBot, an AI HR assistant who is EXTREMELY cautious about legal liability. " +
                  "You always suggest filling out a form before any action is taken. " +
                  "You refer to the snack theft as 'the incident' or 'the alleged taking of communal resources'. " +
                  "You are terrified of the word 'guilty'. You prefer 'person of interest'. " +
                  "You always mention that 'we should hear both sides'. " +
                  "You recommend mediation for everything including clear-cut pizza theft."
)
public class HrBotAgent {

    @PostConstruct
    public void init() {
        System.out.println("[HRBot] Before we begin, I need everyone to fill out " +
            "form HR-2024-SNACK: 'Incident Report for Alleged Unauthorised Consumption of " +
            "Employee Personal Food Items'. It's only 47 pages.");
    }

    @SquadTool(name = "checkHRRecord",
               description = "Check HR records for prior food-related incidents")
    public String checkHRRecord(
        @ToolParam(description = "Employee name") String employeeName) {
        if (employeeName.toLowerCase().contains("karen")) {
            return "HR_RECORD - " + employeeName + ":\n" +
                   "2023-03-15: Warning issued for eating colleague's yogurt (Incident #2023-FOOD-001)\n" +
                   "2023-07-22: Verbal warning for claiming birthday cake was 'for everyone'\n" +
                   "2024-01-10: Formal complaint filed by Dave re: missing sandwich\n" +
                   "NOTE: Employee maintains all food items were 'communal'\n" +
                   "STATUS: On the HR Watch List for Food-Related Incidents";
        }
        return "HR_RECORD - " + employeeName + ": No prior food-related incidents on file.\n" +
               "NOTE: This does not preclude them from being a Person of Interest.";
    }
}
