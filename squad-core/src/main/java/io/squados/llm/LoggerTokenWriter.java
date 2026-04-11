package io.squados.llm;

import java.util.logging.Logger;

/**
 * Buffers streaming tokens and logs the complete response
 * as a single INFO log line when isLast() is received.
 */
public class LoggerTokenWriter implements TokenWriter {

    private final Logger logger;
    private final StringBuilder buffer = new StringBuilder();

    public LoggerTokenWriter() {
        this.logger = Logger.getLogger("squados.streaming");
    }

    public LoggerTokenWriter(String loggerName) {
        this.logger = Logger.getLogger(loggerName);
    }

    @Override
    public void write(StreamToken token) {
        buffer.append(token.text());
        if (token.isLast()) {
            logger.info("[SquadOS streaming] " + buffer);
            buffer.setLength(0);
        }
    }
}
