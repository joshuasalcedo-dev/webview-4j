package io.joshuasalcedo.webview4j.explorer;

import io.joshuasalcedo.webview4j.ui.components.FileNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample data for the explorer's examples: people, orders, sales by month, files. Every example can use it
 * ({@code Seed.people()}), so a table or a chart shows something real-looking from the first run.
 */
public final class Seed {

    private Seed() {}

    /** Someone on a team. */
    public record Person(String id, String name, String email, String role, String status, int projects) {}

    /** An order in a shop. */
    public record Order(String id, String customer, int items, double total, LocalDate placed, boolean paid) {}

    public static List<Person> people() {
        return List.of(
                new Person("p1", "Ada Lovelace", "ada@example.com", "Engineer", "Active", 12),
                new Person("p2", "Grace Hopper", "grace@example.com", "Admiral", "Active", 31),
                new Person("p3", "Alan Turing", "alan@example.com", "Researcher", "Away", 8),
                new Person("p4", "Katherine Johnson", "katherine@example.com", "Mathematician", "Active", 17),
                new Person("p5", "Linus Torvalds", "linus@example.com", "Maintainer", "Offline", 4),
                new Person("p6", "Margaret Hamilton", "margaret@example.com", "Director", "Active", 22));
    }

    public static List<Order> orders() {
        return List.of(
                new Order("INV-1001", "Ada Lovelace", 3, 250.00, LocalDate.of(2026, 11, 2), true),
                new Order("INV-1002", "Grace Hopper", 1, 150.00, LocalDate.of(2026, 11, 5), false),
                new Order("INV-1003", "Alan Turing", 7, 1350.50, LocalDate.of(2026, 11, 9), true),
                new Order("INV-1004", "Katherine Johnson", 2, 89.99, LocalDate.of(2026, 11, 14), true),
                new Order("INV-1005", "Linus Torvalds", 5, 420.00, LocalDate.of(2026, 11, 20), false),
                new Order("INV-1006", "Margaret Hamilton", 4, 610.25, LocalDate.of(2026, 11, 26), true));
    }

    /** Sales and visitors by month: rows for a chart ({@code x = "month"}). */
    public static List<Map<String, Object>> sales() {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun"};
        int[] desktop = {186, 305, 237, 73, 209, 214};
        int[] mobile = {80, 200, 120, 190, 130, 140};
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < months.length; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", months[i]);
            row.put("desktop", desktop[i]);
            row.put("mobile", mobile[i]);
            rows.add(row);
        }
        return rows;
    }

    /** A small project's files. */
    public static List<FileNode> files() {
        Instant now = Instant.parse("2026-11-27T09:00:00Z");
        return List.of(
                FileNode.folder("src",
                        FileNode.folder("main",
                                FileNode.file("App.java", 2_048, now),
                                FileNode.file("Window.java", 4_310, now)),
                        FileNode.file("README.md", 912, now)),
                FileNode.file("build.gradle.kts", 1_204, now),
                FileNode.file("logo.png", 18_400, now));
    }
}
