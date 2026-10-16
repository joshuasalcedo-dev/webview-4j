package io.joshuasalcedo.webview4j.devtools;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.Click;
import io.joshuasalcedo.webview4j.LogRecord;
import io.joshuasalcedo.webview4j.Metrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The dev window's workings, without a screen: its log, its rates, how it says numbers. */
class DevWindowTest {

    private static final Instant AT = Instant.parse("2026-10-16T05:00:00Z");

    private static Metrics.Timing timing(long count) {
        return new Metrics.Timing("t", count, 1, 1, 1, 1);
    }

    private static Metrics metrics(long millis, long framesIn, long framesOut, long bytesIn, long actions) {
        return new Metrics(Duration.ofMillis(millis), framesIn, framesOut, bytesIn, 0, timing(actions), timing(0), List.of());
    }

    @Test
    void ratesArePerSecondBetweenTwoReadings() {
        Rates rates = Rates.between(metrics(1_000, 10, 4, 0, 2), metrics(1_500, 70, 9, 1024, 7));

        assertEquals(120, rates.framesInPerSecond(), 1e-9);
        assertEquals(10, rates.framesOutPerSecond(), 1e-9);
        assertEquals(2, rates.kilobytesInPerSecond(), 1e-9);
        assertEquals(10, rates.actionsPerSecond(), 1e-9);
        assertEquals(Rates.NONE, Rates.between(metrics(5, 1, 1, 1, 1), metrics(5, 9, 9, 9, 9)));
    }

    @Test
    void theLogKeepsWhatHasTheFilterAndTheLatestLinesOnly() {
        LogTableModel log = new LogTableModel();
        log.add(LogTableModel.Entry.of(new LogRecord(LogRecord.Source.PAGE, "wv-1", LogRecord.Level.WARN, "", "careful"), AT));
        log.add(LogTableModel.Entry.of(new LogRecord(LogRecord.Source.NATIVE, null, LogRecord.Level.INFO, "webview4j::runtime", "ready"), AT));
        log.add(LogTableModel.Entry.of(new Click("wv-2", "#go", "button", "Go", 1, 2, Map.of()), AT));

        assertEquals(3, log.getRowCount());
        assertEquals("PAGE", log.getValueAt(0, 1));
        assertEquals("wv-1", log.getValueAt(0, 3));
        assertEquals("webview4j::runtime: ready", log.getValueAt(1, 4));
        assertEquals("EVENT", log.getValueAt(2, 1));
        assertEquals("wv-2", log.getValueAt(2, 3));

        log.filter("warn");
        assertEquals(1, log.getRowCount());
        assertEquals("careful", log.getValueAt(0, 4));
        log.filter("");
        assertEquals(3, log.getRowCount());

        for (int i = 0; i < LogTableModel.MOST; i++) {
            log.add(new LogTableModel.Entry(AT, LogTableModel.Source.JAVA, "INFO", "", "line " + i));
        }
        assertEquals(LogTableModel.MOST, log.total());
        assertEquals(LogTableModel.MOST, log.getRowCount());
        assertEquals("line 0", log.getValueAt(0, 4));
        log.clear();
        assertEquals(0, log.getRowCount());
    }

    @Test
    void itOpensOnlyWhenAskedFor() {
        assertTrue(DevWindow.wanted("true", null));
        assertTrue(DevWindow.wanted(null, "TRUE"));
        assertFalse(DevWindow.wanted("false", "true"));
        assertFalse(DevWindow.wanted(null, null));
    }

    @Test
    void numbersReadAsPeopleReadThem() {
        assertEquals("0.29", DevWindow.ms(0.2913));
        assertEquals("12.4", DevWindow.ms(12.41));
        assertEquals("250", DevWindow.ms(250.4));
        assertEquals("1:05", DevWindow.uptime(Duration.ofSeconds(65)));
        assertEquals("1:00:05", DevWindow.uptime(Duration.ofSeconds(3605)));
    }
}
