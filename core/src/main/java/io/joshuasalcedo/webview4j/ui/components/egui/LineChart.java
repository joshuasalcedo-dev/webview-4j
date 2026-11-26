package io.joshuasalcedo.webview4j.ui.components.egui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Lines that keep coming: egui-4j's {@code line-chart}, drawn by Rust in the page. Pan and zoom with the mouse,
 * double-click to fit again, click for the nearest point. Java keeps the points; the chart shows what it has.
 * <pre>{@code
 * LineChart cpu = new LineChart("CPU over time").xLabel("s").yLabel("%").maxPoints(600).height(260);
 * cpu.series("CPU", 0x0A84FF);
 * timer: cpu.append("CPU", seconds, load);      // from any thread: the window draws again
 * cpu.onClick((x, y) -> status.setText("%.1f s: %.0f %%".formatted(x, y)));
 * }</pre>
 */
public final class LineChart extends EguiComponent<LineChart> {

    /** One line: its points (x, y) and its colour (0xRRGGBB, or null for the chart's own). */
    private record Line(String name, List<double[]> points, Integer color) {}

    private final Map<String, Line> lines = new LinkedHashMap<>();
    private Integer maxPoints;

    public LineChart(String label) {
        super("line-chart", label);
    }

    /** A line named {@code name}, in the chart's next colour (no-op if it is there). */
    public LineChart series(String name) {
        return series(name, null);
    }

    /** A line named {@code name} in {@code rgb} (0xRRGGBB); an existing one takes the colour. */
    public LineChart series(String name, Integer rgb) {
        Objects.requireNonNull(name, "name");
        if (rgb != null && (rgb < 0 || rgb > 0xFFFFFF)) throw new IllegalArgumentException("a colour is 0xRRGGBB: " + Integer.toHexString(rgb));
        synchronized (lines) {
            Line before = lines.get(name);
            lines.put(name, new Line(name, before == null ? new ArrayList<>() : before.points(), rgb));
        }
        return send();
    }

    /** Adds a point to {@code series} (made if new): from any thread, the window draws again. */
    public LineChart append(String series, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return this;
        synchronized (lines) {
            Line line = lines.computeIfAbsent(Objects.requireNonNull(series, "series"), n -> new Line(n, new ArrayList<>(), null));
            line.points().add(new double[] {x, y});
            trim(line);
        }
        return send();
    }

    /** All of {@code series}'s points at once, in place of those it had. */
    public LineChart setPoints(String series, List<double[]> points) {
        synchronized (lines) {
            Line line = lines.computeIfAbsent(Objects.requireNonNull(series, "series"), n -> new Line(n, new ArrayList<>(), null));
            line.points().clear();
            for (double[] p : points) if (p.length == 2 && Double.isFinite(p[0]) && Double.isFinite(p[1])) line.points().add(p.clone());
            trim(line);
        }
        return send();
    }

    /** Takes every point away (the lines stay). */
    public LineChart clear() {
        synchronized (lines) {
            lines.values().forEach(l -> l.points().clear());
        }
        return send();
    }

    /** The number of points {@code series} has now. */
    public int pointCount(String series) {
        synchronized (lines) {
            Line line = lines.get(series);
            return line == null ? 0 : line.points().size();
        }
    }

    /** Each line keeps only its newest {@code max} points (a stream that runs for hours). */
    public LineChart maxPoints(int max) {
        if (max < 1) throw new IllegalArgumentException("maxPoints is at least 1: " + max);
        synchronized (lines) {
            maxPoints = max;
            lines.values().forEach(this::trim);
        }
        eguiProp("maxPoints", max);
        return send();
    }

    public LineChart xLabel(String label) { return eguiProp("xLabel", label); }

    public LineChart yLabel(String label) { return eguiProp("yLabel", label); }

    /** Whether the lines' names are shown (on at first). */
    public LineChart legend(boolean shown) { return eguiProp("legend", shown); }

    /** A click on the chart: the value under the pointer (x, y). */
    public LineChart onClick(BiConsumer<Double, Double> action) {
        Objects.requireNonNull(action, "action");
        return addEventListener(e -> {
            if (e.name().equals("click") && e.number("x") != null && e.number("y") != null) action.accept(e.number("x"), e.number("y"));
        });
    }

    private void trim(Line line) {
        if (maxPoints != null && line.points().size() > maxPoints) line.points().subList(0, line.points().size() - maxPoints).clear();
    }

    /** The lines as the Rust chart reads them: {@code series: [{name, points: [[x, y]...], color: "#rrggbb"}]}. */
    private LineChart send() {
        List<Map<String, Object>> series = new ArrayList<>();
        synchronized (lines) {
            for (Line line : lines.values()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", line.name());
                List<List<Double>> points = new ArrayList<>(line.points().size());
                for (double[] p : line.points()) points.add(List.of(p[0], p[1]));
                s.put("points", points);
                if (line.color() != null) s.put("color", "#%06x".formatted(line.color()));
                series.add(s);
            }
        }
        return eguiProp("series", series);
    }
}
