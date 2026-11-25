package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Cookie;
import io.joshuasalcedo.webview4j.PageStorage;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ViewInfo;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * The dev window's Storage tab, for one view: its cookies (every one of its browser profile, HttpOnly ones too),
 * its page's localStorage and sessionStorage; refreshed on demand, an entry deleted, a storage cleared.
 */
final class StoragePanel {

    private final App app;
    private final JComboBox<String> views = new JComboBox<>();
    private final DefaultTableModel cookies = table("Name", "Value", "Domain", "Path", "HttpOnly", "Secure");
    private final DefaultTableModel local = table("Key", "Value");
    private final DefaultTableModel session = table("Key", "Value");
    private final JLabel said = new JLabel(" ");
    private final JPanel panel = new JPanel(new BorderLayout());
    private List<Cookie> shownCookies = List.of();

    StoragePanel(App app) {
        this.app = app;
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        views.addActionListener(e -> refresh());

        JTable cookieTable = new JTable(cookies);
        JButton deleteCookie = new JButton("Delete cookie");
        deleteCookie.addActionListener(e -> {
            int row = cookieTable.getSelectedRow();
            View view = view();
            if (row < 0 || view == null) return;
            done(view.deleteCookie(shownCookies.get(cookieTable.convertRowIndexToModel(row))), "Deleted the cookie");
        });

        JTable localTable = new JTable(local);
        JTable sessionTable = new JTable(session);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Cookies", withButtons(cookieTable, deleteCookie));
        tabs.addTab("Local storage", storageTab(localTable, View::localStorage));
        tabs.addTab("Session storage", storageTab(sessionTable, View::sessionStorage));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("View"));
        bar.add(views);
        bar.add(refresh);
        bar.add(said);
        panel.add(bar, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
    }

    JPanel component() {
        return panel;
    }

    /** The open views, as the chooser lists them (from the dev window's tick); keeps the chosen one. */
    void views(List<ViewInfo> open) {
        Object chosen = views.getSelectedItem();
        List<String> ids = open.stream().map(ViewInfo::id).toList();
        boolean same = ids.size() == views.getItemCount() && ids.stream().allMatch(id -> ((javax.swing.DefaultComboBoxModel<String>) views.getModel()).getIndexOf(id) >= 0);
        if (same) return;
        views.removeAllItems();
        ids.forEach(views::addItem);
        if (chosen != null && ids.contains(chosen)) views.setSelectedItem(chosen);
    }

    private View view() {
        Object id = views.getSelectedItem();
        return id == null ? null : app.view((String) id);
    }

    /** Reads the chosen view's cookies and storages again. */
    void refresh() {
        View view = view();
        if (view == null) return;
        view.allCookies().thenAccept(list -> SwingUtilities.invokeLater(() -> {
            shownCookies = list;
            cookies.setRowCount(0);
            for (Cookie c : list) cookies.addRow(new Object[] {c.name(), c.value(), c.domain(), c.path(), c.httpOnly(), c.secure()});
        })).exceptionally(this::failed);
        fill(view.localStorage(), local);
        fill(view.sessionStorage(), session);
    }

    private void fill(PageStorage storage, DefaultTableModel model) {
        storage.entries().thenAccept(entries -> SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (Map.Entry<String, String> e : entries.entrySet()) model.addRow(new Object[] {e.getKey(), e.getValue()});
        })).exceptionally(this::failed);
    }

    private JPanel storageTab(JTable table, Function<View, PageStorage> which) {
        JButton remove = new JButton("Remove key");
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            View view = view();
            if (row < 0 || view == null) return;
            String key = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), 0));
            done(which.apply(view).remove(key), "Removed " + key);
        });
        JButton clear = new JButton("Clear all");
        clear.addActionListener(e -> {
            View view = view();
            if (view != null) done(which.apply(view).clear(), "Cleared");
        });
        return withButtons(table, remove, clear);
    }

    private void done(CompletableFuture<Void> action, String what) {
        action.thenRun(() -> SwingUtilities.invokeLater(() -> {
            said.setText(what);
            refresh();
        })).exceptionally(this::failed);
    }

    private Void failed(Throwable e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        SwingUtilities.invokeLater(() -> said.setText("Could not: " + cause.getMessage()));
        return null;
    }

    private static JPanel withButtons(JTable table, JButton... buttons) {
        table.setAutoCreateRowSorter(true);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton b : buttons) bar.add(b);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    private static DefaultTableModel table(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
}
