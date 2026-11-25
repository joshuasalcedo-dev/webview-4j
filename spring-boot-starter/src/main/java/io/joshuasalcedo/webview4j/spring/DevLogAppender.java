package io.joshuasalcedo.webview4j.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import io.joshuasalcedo.webview4j.DevLogger;

/** The app's Logback lines (Spring Boot logs with Logback), to the dev logger. */
final class DevLogAppender extends AppenderBase<ILoggingEvent> {

    static final String NAME = "WEBVIEW4J_DEV_LOGGER";

    private final DevLogSink sink;

    DevLogAppender(DevLogSink sink) {
        this.sink = sink;
    }

    /**
     * On Logback's root logger, when Logback is the app's logging. One at a time: an earlier one (an app context
     * started again in the same JVM, e.g. by Spring's devtools) is replaced, so lines go to this sink, not a gone one.
     */
    static boolean install(DevLogSink sink) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) return false;
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(NAME) instanceof DevLogAppender earlier && earlier.sink == sink) return true;
        uninstall();
        DevLogAppender appender = new DevLogAppender(sink);
        appender.setContext(context);
        appender.setName(NAME);
        appender.start();
        root.addAppender(appender);
        sink.logbackInstalled();
        return true;
    }

    /** Off Logback's root logger (the app context closed). */
    static void uninstall() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) return;
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var earlier = root.getAppender(NAME);
        if (earlier != null) {
            root.detachAppender(earlier);
            earlier.stop();
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        String logger = event.getLoggerName();
        // the dev logger's own lines (about sending to its window) would feed themselves
        if (logger != null && logger.startsWith(DevLogger.class.getName())) return;
        String message = event.getFormattedMessage();
        IThrowableProxy thrown = event.getThrowableProxy();
        if (thrown != null) message += " (" + thrown.getClassName() + (thrown.getMessage() != null ? ": " + thrown.getMessage() : "") + ")";
        sink.add(DevLogger.log(event.getTimeStamp(), level(event.getLevel()), logger, message));
    }

    static String level(Level level) {
        if (level == null) return "info";
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "error";
            case Level.WARN_INT -> "warn";
            case Level.INFO_INT -> "info";
            case Level.DEBUG_INT -> "debug";
            default -> "trace";
        };
    }
}
