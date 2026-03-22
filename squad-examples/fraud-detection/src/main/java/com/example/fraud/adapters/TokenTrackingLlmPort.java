package com.example.fraud.adapters;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;
import java.util.concurrent.atomic.AtomicInteger;
public class TokenTrackingLlmPort implements LlmPort {
    private final LlmPort delegate;
    private final AtomicInteger totalPrompt = new AtomicInteger(0);
    private final AtomicInteger totalCompletion = new AtomicInteger(0);
    private volatile int lastPrompt = 0;
    private volatile int lastCompletion = 0;
    public TokenTrackingLlmPort(LlmPort delegate) { this.delegate = delegate; }
    @Override
    public LlmResponse chat(String sys, String user, LlmOptions opts) {
        LlmResponse r = delegate.chat(sys, user, opts);
        int p = r.promptTokens() > 0 ? r.promptTokens() : (sys.length() + user.length()) / 4;
        int c = r.completionTokens() > 0 ? r.completionTokens()
              : (r.content() != null ? r.content().length() / 4 : 0);
        totalPrompt.addAndGet(p);
        totalCompletion.addAndGet(c);
        lastPrompt = p; lastCompletion = c;
        return new LlmResponse(r.content(), p, c, r.model());
    }
    @Override
    public <T> T chatStructured(String sys, String user, Class<T> t, LlmOptions opts) {
        return delegate.chatStructured(sys, user, t, opts);
    }
    public int getTotalTokens()      { return totalPrompt.get() + totalCompletion.get(); }
    public int getTotalPrompt()      { return totalPrompt.get(); }
    public int getTotalCompletion()  { return totalCompletion.get(); }
    public int getLastPrompt()       { return lastPrompt; }
    public int getLastCompletion()   { return lastCompletion; }
    public void reset()              { totalPrompt.set(0); totalCompletion.set(0); }
}