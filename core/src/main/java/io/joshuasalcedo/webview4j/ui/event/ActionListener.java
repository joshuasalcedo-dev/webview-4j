package io.joshuasalcedo.webview4j.ui.event;

/** What runs when the user acts on a component (Swing's {@code ActionListener}): {@code button.addActionListener(e -> save())}. */
@FunctionalInterface
public interface ActionListener {

    void actionPerformed(ActionEvent e);
}
