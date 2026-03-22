package io.squados.improve;

import java.time.Instant;
import java.util.UUID;

/**
 * A single feedback example stored in the FeedbackStore.
 * Contains the input, output, label (GOOD/BAD), and human feedback note.
 */
public class FeedbackExample {

    public enum Label { GOOD, BAD }

    private final String  id;
    private final String  methodLabel;  // e.g. "LoanAgent.underwriteLoan"
    private final String  input;        // what was given to the agent
    private final String  output;       // what the agent produced
    private final Label   label;        // GOOD or BAD
    private final String  note;         // human feedback note
    private final Instant createdAt;
    private final float[] embedding;    // semantic embedding (optional)

    public FeedbackExample(String methodLabel, String input, String output,
                            Label label, String note) {
        this.id          = UUID.randomUUID().toString();
        this.methodLabel = methodLabel;
        this.input       = input;
        this.output      = output;
        this.label       = label;
        this.note        = note;
        this.createdAt   = Instant.now();
        this.embedding   = null;
    }

    public FeedbackExample withEmbedding(float[] emb) {
        return new FeedbackExample(methodLabel, input, output, label, note) {
            { /* embedding set via field */ }
        };
    }

    public String  getId()          { return id; }
    public String  getMethodLabel() { return methodLabel; }
    public String  getInput()       { return input; }
    public String  getOutput()      { return output; }
    public Label   getLabel()       { return label; }
    public String  getNote()        { return note; }
    public Instant getCreatedAt()   { return createdAt; }
    public float[] getEmbedding()   { return embedding; }
    public boolean isGood()         { return label == Label.GOOD; }
    public boolean isBad()          { return label == Label.BAD; }

    /** Format as a few-shot example for injection into agent prompt. */
    public String toPromptExample(boolean isNegative) {
        StringBuilder sb = new StringBuilder();
        if (isNegative) {
            sb.append("EXAMPLE OF WHAT TO AVOID:\n");
        } else {
            sb.append("EXAMPLE OF A GOOD RESPONSE:\n");
        }
        sb.append("INPUT: ").append(input.length() > 200
            ? input.substring(0, 200) + "..." : input).append("\n");
        sb.append("OUTPUT: ").append(output.length() > 300
            ? output.substring(0, 300) + "..." : output).append("\n");
        if (note != null && !note.isBlank()) {
            sb.append("WHY: ").append(note).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("FeedbackExample{id=%s, label=%s, method=%s}",
            id, label, methodLabel);
    }
}