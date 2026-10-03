package ac.grim.grimac.platform.minestom.utils;

import org.slf4j.LoggerFactory;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Slf4jLogger extends Logger {
    private final org.slf4j.Logger delegate;

    public Slf4jLogger(String name) {
        super(name, null);
        this.delegate = LoggerFactory.getLogger(name);
        setLevel(Level.ALL);
    }

    @Override
    public void log(LogRecord record) {
        int level = record.getLevel().intValue();
        String message = record.getMessage();
        Throwable thrown = record.getThrown();
        if (level >= Level.SEVERE.intValue()) {
            delegate.error(message, thrown);
        } else if (level >= Level.WARNING.intValue()) {
            delegate.warn(message, thrown);
        } else if (level >= Level.INFO.intValue()) {
            delegate.info(message, thrown);
        } else if (level >= Level.FINE.intValue()) {
            delegate.debug(message, thrown);
        } else {
            delegate.trace(message, thrown);
        }
    }
}
