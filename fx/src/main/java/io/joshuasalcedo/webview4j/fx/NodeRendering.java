package io.joshuasalcedo.webview4j.fx;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.joshuasalcedo.webview4j.DevToolsEvent;
import io.joshuasalcedo.webview4j.Subscription;
import io.joshuasalcedo.webview4j.View;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;

/**
 * The page drawn as a JavaFX node: its frames come from the DevTools screencast into an {@link ImageView}, and the
 * person's mouse and keys go back to it as DevTools input. Its native window stays under the node, all but invisible
 * and letting the mouse through, so that the engine keeps drawing and its pop-ups open in the right place.
 */
final class NodeRendering {

    /** One notch of a wheel, in the page's pixels: as a browser scrolls. */
    private static final double WHEEL = 2.5;

    private final Region node;
    private final ImageView image = new ImageView();
    private final Runnable firstFrame;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("webview4j-fx-frames").factory());
    private View view;
    private Subscription frames;
    private boolean painted;
    /** The last mouse move, sent once a frame: moves come faster than the page draws. */
    private MouseEvent move;
    private long cursorAsked;

    NodeRendering(Region node, Runnable firstFrame) {
        this.node = node;
        this.firstFrame = firstFrame;
        image.setSmooth(true);
        image.setPreserveRatio(false);
        image.setManaged(false);
        node.setFocusTraversable(true);
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> move = e);
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> move = e);
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            node.requestFocus();
            mouse("mousePressed", e, e.getButton(), e.getClickCount());
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> mouse("mouseReleased", e, e.getButton(), e.getClickCount()));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> node.setCursor(Cursor.DEFAULT));
        node.addEventHandler(ScrollEvent.SCROLL, this::wheel);
        node.addEventHandler(KeyEvent.KEY_PRESSED, this::keyDown);
        node.addEventHandler(KeyEvent.KEY_RELEASED, this::keyUp);
        node.addEventHandler(KeyEvent.KEY_TYPED, this::typed);
    }

    ImageView image() {
        return image;
    }

    boolean painted() {
        return painted;
    }

    void layout(double width, double height) {
        image.resizeRelocate(0, 0, width, height);
        image.setFitWidth(width);
        image.setFitHeight(height);
    }

    /** Starts drawing this view's page: its screencast, and the page kept as focused as a window in front. */
    void start(View view) {
        stop();
        this.view = view;
        painted = false;
        frames = view.subscribe(DevToolsEvent.class, e -> {
            if ("Page.screencastFrame".equals(e.event())) frame(view, e.params());
        });
        view.listenDevTools("Page.screencastFrame")
                .thenCompose(nothing -> view.devTools("Emulation.setFocusEmulationEnabled", Map.of("enabled", true)))
                .thenCompose(nothing -> view.devTools("Page.startScreencast", Map.of("format", "jpeg", "quality", 92, "everyNthFrame", 1)));
    }

    void stop() {
        if (frames != null) frames.close();
        frames = null;
        view = null;
    }

    void close() {
        stop();
        decoder.shutdownNow();
    }

    /** Sends the last mouse move: once a frame, from the pane's timer. */
    void flush() {
        MouseEvent e = move;
        move = null;
        if (e == null || view == null) return;
        MouseButton held = e.isPrimaryButtonDown() ? MouseButton.PRIMARY : e.isSecondaryButtonDown() ? MouseButton.SECONDARY
                : e.isMiddleButtonDown() ? MouseButton.MIDDLE : MouseButton.NONE;
        mouse("mouseMoved", e, held, 0);
        long now = System.nanoTime();
        if (now - cursorAsked > 120_000_000L) { // the pointer's shape, a few times a second
            cursorAsked = now;
            cursor(e.getX(), e.getY());
        }
    }

    // ---- frames ----

    private void frame(View from, Map<String, Object> params) {
        Object data = params.get("data");
        Object session = params.get("sessionId");
        decoder.execute(() -> {
            try {
                if (data instanceof String base64) {
                    Image next = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
                    Platform.runLater(() -> {
                        if (view != from) return;
                        image.setImage(next);
                        if (!painted) {
                            painted = true;
                            firstFrame.run();
                        }
                    });
                }
            } finally {
                // The next frame comes once this one is acknowledged: the page never outruns JavaFX.
                if (session != null) from.devTools("Page.screencastFrameAck", Map.of("sessionId", session));
            }
        });
    }

    // ---- the mouse ----

    private void mouse(String type, MouseEvent e, MouseButton button, int clicks) {
        if (view == null) return;
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", type);
        input.put("x", e.getX());
        input.put("y", e.getY());
        input.put("modifiers", modifiers(e.isAltDown(), e.isControlDown(), e.isMetaDown(), e.isShiftDown()));
        input.put("button", switch (button) {
            case PRIMARY -> "left";
            case SECONDARY -> "right";
            case MIDDLE -> "middle";
            case BACK -> "back";
            case FORWARD -> "forward";
            default -> "none";
        });
        input.put("buttons", (e.isPrimaryButtonDown() ? 1 : 0) | (e.isSecondaryButtonDown() ? 2 : 0) | (e.isMiddleButtonDown() ? 4 : 0));
        if (clicks > 0) input.put("clickCount", clicks);
        view.devTools("Input.dispatchMouseEvent", input);
        e.consume();
    }

    private void wheel(ScrollEvent e) {
        if (view == null) return;
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "mouseWheel");
        input.put("x", e.getX());
        input.put("y", e.getY());
        input.put("deltaX", -e.getDeltaX() * WHEEL);
        input.put("deltaY", -e.getDeltaY() * WHEEL);
        input.put("modifiers", modifiers(e.isAltDown(), e.isControlDown(), e.isMetaDown(), e.isShiftDown()));
        view.devTools("Input.dispatchMouseEvent", input);
        e.consume();
    }

    /** The pointer the page asks for where the mouse is: a hand on links, a bar on text. */
    private void cursor(double x, double y) {
        view.eval("(() => { const e = document.elementFromPoint(" + x + ", " + y + "); if (!e) return 'default';"
                + " const c = getComputedStyle(e).cursor; if (c && c !== 'auto') return c;"
                + " if (e.closest('a[href], button, [role=button], summary, label, select')) return 'pointer';"
                + " if (e.isContentEditable || e.matches('input:not([type=checkbox]):not([type=radio]):not([type=button]):not([type=submit]), textarea')) return 'text';"
                + " return 'default'; })()")
                .thenAccept(c -> Platform.runLater(() -> node.setCursor(switch (String.valueOf(c)) {
                    case "pointer" -> Cursor.HAND;
                    case "text", "vertical-text" -> Cursor.TEXT;
                    case "move", "grab", "grabbing", "all-scroll" -> Cursor.MOVE;
                    case "crosshair" -> Cursor.CROSSHAIR;
                    case "wait", "progress" -> Cursor.WAIT;
                    case "ew-resize", "col-resize", "e-resize", "w-resize" -> Cursor.H_RESIZE;
                    case "ns-resize", "row-resize", "n-resize", "s-resize" -> Cursor.V_RESIZE;
                    case "not-allowed", "no-drop" -> Cursor.DISAPPEAR;
                    default -> Cursor.DEFAULT;
                })));
    }

    // ---- the keyboard ----

    private void keyDown(KeyEvent e) {
        if (view == null) return;
        Map<String, Object> input = key("rawKeyDown", e);
        String command = command(e);
        if (command != null) input.put("commands", List.of(command));
        view.devTools("Input.dispatchKeyEvent", input);
        e.consume(); // Tab, arrows, Enter: the page's, not JavaFX's focus traversal
    }

    private void keyUp(KeyEvent e) {
        if (view == null) return;
        view.devTools("Input.dispatchKeyEvent", key("keyUp", e));
        e.consume();
    }

    private void typed(KeyEvent e) {
        if (view == null) return;
        String text = e.getCharacter();
        if (text == null || text.isEmpty() || text.equals(KeyEvent.CHAR_UNDEFINED) || (e.isControlDown() && !e.isAltDown())
                || text.charAt(0) == 8 || text.charAt(0) == 27 || text.charAt(0) == 127) {
            e.consume();
            return;
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "char");
        input.put("text", text);
        input.put("unmodifiedText", text);
        input.put("modifiers", modifiers(e.isAltDown(), e.isControlDown(), e.isMetaDown(), e.isShiftDown()));
        view.devTools("Input.dispatchKeyEvent", input);
        e.consume();
    }

    private static Map<String, Object> key(String type, KeyEvent e) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", type);
        input.put("windowsVirtualKeyCode", virtualKey(e.getCode()));
        input.put("nativeVirtualKeyCode", virtualKey(e.getCode()));
        input.put("key", keyName(e));
        input.put("modifiers", modifiers(e.isAltDown(), e.isControlDown(), e.isMetaDown(), e.isShiftDown()));
        return input;
    }

    /** The editing command a shortcut runs: the page gets the keys, the engine does the copying. */
    private static String command(KeyEvent e) {
        if (!e.isShortcutDown()) return null;
        return switch (e.getCode()) {
            case C -> "copy";
            case X -> "cut";
            case V -> "paste";
            case A -> "selectAll";
            case Z -> e.isShiftDown() ? "redo" : "undo";
            case Y -> "redo";
            default -> null;
        };
    }

    /** DevTools' modifier bits: Alt 1, Ctrl 2, Meta 4, Shift 8. */
    static int modifiers(boolean alt, boolean control, boolean meta, boolean shift) {
        return (alt ? 1 : 0) | (control ? 2 : 0) | (meta ? 4 : 0) | (shift ? 8 : 0);
    }

    /** Windows' virtual-key code for a JavaFX key: the same, but for the keys JavaFX numbers as Java did. */
    static int virtualKey(KeyCode code) {
        return switch (code) {
            case ENTER -> 13;
            case DELETE -> 46;
            case INSERT -> 45;
            case COMMA -> 188;
            case MINUS -> 189;
            case PERIOD -> 190;
            case SLASH -> 191;
            case SEMICOLON -> 186;
            case EQUALS -> 187;
            case OPEN_BRACKET -> 219;
            case BACK_SLASH -> 220;
            case CLOSE_BRACKET -> 221;
            case QUOTE -> 222;
            case BACK_QUOTE -> 192;
            case WINDOWS -> 91;
            case CONTEXT_MENU -> 93;
            default -> code.getCode();
        };
    }

    /** The DOM's name for the key ({@code KeyboardEvent.key}): what pages check. */
    static String keyName(KeyEvent e) {
        return switch (e.getCode()) {
            case ENTER -> "Enter";
            case BACK_SPACE -> "Backspace";
            case TAB -> "Tab";
            case ESCAPE -> "Escape";
            case DELETE -> "Delete";
            case INSERT -> "Insert";
            case HOME -> "Home";
            case END -> "End";
            case PAGE_UP -> "PageUp";
            case PAGE_DOWN -> "PageDown";
            case LEFT -> "ArrowLeft";
            case RIGHT -> "ArrowRight";
            case UP -> "ArrowUp";
            case DOWN -> "ArrowDown";
            case SHIFT -> "Shift";
            case CONTROL -> "Control";
            case ALT -> "Alt";
            case WINDOWS, META, COMMAND -> "Meta";
            case SPACE -> " ";
            case F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12 -> e.getCode().getName();
            default -> {
                String text = e.getText();
                yield text == null || text.isEmpty() ? e.getCode().getName() : text;
            }
        };
    }
}
