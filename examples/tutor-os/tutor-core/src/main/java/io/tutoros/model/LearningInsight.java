package io.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Structured output from {@code BookCoachAgent} — one chapter, one lens.
 *
 * <p>The agent is invoked with a {@code mode} string (basic /
 * intermediate / advanced / usage / history / future) and produces a
 * LearningInsight whose body shape varies by mode:
 *
 * <table>
 *   <tr><th>mode</th>           <th>body emphasis</th>                                  <th>question populated?</th></tr>
 *   <tr><td>basic</td>          <td>definition + recall</td>                            <td>yes</td></tr>
 *   <tr><td>intermediate</td>   <td>application + worked example</td>                   <td>yes</td></tr>
 *   <tr><td>advanced</td>       <td>synthesis / evaluation / multi-step</td>            <td>yes</td></tr>
 *   <tr><td>usage</td>          <td>concrete real-life examples + where it's applied</td><td>no</td></tr>
 *   <tr><td>history</td>        <td>discovery, key figures, evolution</td>              <td>no</td></tr>
 *   <tr><td>future</td>         <td>open problems, research frontier, careers</td>      <td>no</td></tr>
 * </table>
 *
 * <p>The question + modelAnswer fields are populated only for the
 * three quiz-style lenses. The UI uses their presence as the toggle
 * between "show me an explanation" and "ask me a question." Mastery
 * deltas only fire on the answered-mode path (PR-2 wires this through
 * {@code MasteryService.recordFromFeedback} with source="book").
 */
public class LearningInsight {

    @OutputField(
        description = "Which lens this insight is for. One of: " +
                      "basic, intermediate, advanced, usage, history, future.",
        example     = "basic"
    )
    public String mode;

    @OutputField(
        description = "Concept the insight focuses on — must be one of the " +
                      "chapter's extracted concept tags. Used as the mastery " +
                      "key when the learner answers an attached question.",
        example     = "photosynthesis"
    )
    public String concept;

    @OutputField(
        description = "Main body of the insight, in markdown. For basic/" +
                      "intermediate/advanced this introduces or motivates " +
                      "the concept; for usage/history/future it IS the answer.",
        example     = "Photosynthesis is the process plants use to convert " +
                      "light energy into stored chemical energy…"
    )
    public String body;

    @OutputField(
        description = "Quiz-style question text. Populated for basic / " +
                      "intermediate / advanced modes only; empty/null for " +
                      "usage / history / future.",
        example     = "Explain in your own words what photosynthesis converts and produces."
    )
    public String question;

    @OutputField(
        description = "Model answer to the question above — used by the UI's " +
                      "show-answer affordance and by the AssessmentAgent when " +
                      "the learner submits a response. Empty when question is empty.",
        example     = "Photosynthesis converts light energy + CO2 + water into " +
                      "glucose and oxygen."
    )
    public String modelAnswer;

    @OutputField(
        description = "Question difficulty. EASY for basic, MEDIUM for " +
                      "intermediate, HARD for advanced. Empty for non-quiz modes.",
        example     = "EASY"
    )
    public String difficulty;

    @OutputField(
        description = "1–3 follow-up prompts the learner can ask to go deeper. " +
                      "Newline-separated. Optional.",
        example     = "What role do chloroplasts play?\nHow is photosynthesis " +
                      "different from cellular respiration?"
    )
    public String followUps;

    @OutputField(
        description = "Source citation — pages from the uploaded chapter " +
                      "this insight draws from. Empty when the insight " +
                      "extends beyond the chapter (history / future).",
        example     = "pp. 42–47"
    )
    public String sourcePages;
}
