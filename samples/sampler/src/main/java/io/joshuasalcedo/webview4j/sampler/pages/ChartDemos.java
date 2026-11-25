package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.List;

/** The Sampler's pages: charts as shadcn/ui's (Recharts), from Java records. */
final class ChartDemos implements Demos {

    record Visits(String month, int desktop, int mobile) {}

    record Share(String browser, int visitors) {}

    record Skill(String area, int team, int target) {}

    private static final List<Visits> VISITS = List.of(
            new Visits("Jan", 186, 80), new Visits("Feb", 305, 200), new Visits("Mar", 237, 120),
            new Visits("Apr", 73, 190), new Visits("May", 209, 130), new Visits("Jun", 214, 140));

    private static final List<Share> SHARES = List.of(
            new Share("Chrome", 275), new Share("Safari", 200), new Share("Firefox", 187), new Share("Edge", 173), new Share("Other", 90));

    private static final List<Skill> SKILLS = List.of(
            new Skill("Java", 90, 80), new Skill("Rust", 60, 75), new Skill("React", 75, 70),
            new Skill("SQL", 70, 80), new Skill("Ops", 55, 65), new Skill("Design", 45, 60));

    private static final List<ChartSeries> DEVICES = List.of(new ChartSeries("desktop", "Desktop"), new ChartSeries("mobile", "Mobile"));

    private Chart.Kind kind = Chart.Kind.BAR;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Content", "Charts", () -> column(
                        segmented("Kind", Demos.ordered("BAR", "Bar", "LINE", "Line", "AREA", "Area"))
                                .value(kind.name()).onChange(v -> kind = Chart.Kind.valueOf(v)),
                        new Chart(kind, VISITS, "month", DEVICES).title("Visitors").description("January to June")
                                .stacked(kind == Chart.Kind.AREA),
                        row(new Chart(Chart.Kind.PIE, SHARES, "browser", List.of(new ChartSeries("visitors", "Visitors"))).title("Browsers").height(220).grow(),
                                new Chart(Chart.Kind.RADAR, SKILLS, "area", List.of(new ChartSeries("team", "Team"), new ChartSeries("target", "Target")))
                                        .title("Skills").unit("%").height(220).grow()).gap(12).align("stretch"),
                        new Chart(Chart.Kind.BAR, VISITS, "month", List.of(new ChartSeries("desktop", "Desktop"))).horizontal()
                                .title("Desktop visitors").height(220)).gap(12),
                """
                record Visits(String month, int desktop, int mobile) {}

                new Chart(Chart.Kind.BAR, visits, "month", List.of(new ChartSeries("desktop", "Desktop"), new ChartSeries("mobile", "Mobile")))
                        .title("Visitors").description("January to June")
                new Chart(Chart.Kind.PIE, shares, "browser", List.of(new ChartSeries("visitors", "Visitors")))
                new Chart(Chart.Kind.RADAR, skills, "area", ...).unit("%")      // also LINE, AREA (.stacked()), RADIAL_BAR""",
                Chart.class);
    }
}
