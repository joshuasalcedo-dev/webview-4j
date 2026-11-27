package io.joshuasalcedo.swaswa.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * What a test needs of Windows itself, through user32: a window found by its title, its styles as Windows has them
 * (to check that a setting reached the real window), and a system dialog answered as a person would (a path typed,
 * then OK or Cancel), so dialogs are tested without anyone at the keyboard.
 */
final class Win32 {

    static final int GWL_STYLE = -16, GWL_EXSTYLE = -20;
    static final long WS_CAPTION = 0x00C00000L, WS_THICKFRAME = 0x00040000L, WS_MINIMIZEBOX = 0x00020000L,
            WS_MAXIMIZEBOX = 0x00010000L;
    static final long WS_EX_TOPMOST = 0x00000008L, WS_EX_NOACTIVATE = 0x08000000L, WS_EX_LAYERED = 0x00080000L;
    static final int BM_CLICK = 0x00F5, WM_SETTEXT = 0x000C, WM_COMMAND = 0x0111, WM_GETICON = 0x007F;
    static final int IDOK = 1, IDCANCEL = 2;
    /** The file name box of a common file dialog (cmb13), and the edit inside it in older ones (edt1). */
    private static final int FILE_NAME_COMBO = 0x47C, FILE_NAME_SAVE = 0x3E9, FILE_NAME_EDIT = 0x480;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());
    private static final MethodHandle FIND_WINDOW = handle("FindWindowW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle FIND_WINDOW_EX = handle("FindWindowExW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GET_DLG_CTRL_ID = handle("GetDlgCtrlID", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle GET_CLASS_NAME = handle("GetClassNameW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle GET_WINDOW_RECT = handle("GetWindowRect", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_CLIENT_RECT = handle("GetClientRect", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_FOREGROUND = handle("GetForegroundWindow", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle GET_WINDOW_TEXT = handle("GetWindowTextW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle GET_CURSOR_POS = handle("GetCursorPos", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle SET_CURSOR_POS = handle("SetCursorPos", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle MOUSE_EVENT = handle("mouse_event", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));
    private static final MethodHandle SET_WINDOW_POS = handle("SetWindowPos", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle SEND_MESSAGE_PLAIN = handle("SendMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle KEYBD_EVENT = handle("keybd_event", FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_BYTE, java.lang.foreign.ValueLayout.JAVA_BYTE, JAVA_INT, JAVA_LONG));
    private static final MethodHandle DWM_GET = LINKER.downcallHandle(
            SymbolLookup.libraryLookup("dwmapi", Arena.global()).find("DwmGetWindowAttribute").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle GET_CURSOR_INFO = handle("GetCursorInfo", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle LOAD_CURSOR = handle("LoadCursorW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GET_DLG_ITEM = handle("GetDlgItem", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle SEND_MESSAGE = handle("SendMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS));
    private static final MethodHandle POST_MESSAGE = handle("PostMessageW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle GET_WINDOW_LONG = handle("GetWindowLongPtrW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle IS_WINDOW_VISIBLE = handle("IsWindowVisible", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private Win32() {}

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(USER32.find(name).orElseThrow(), descriptor);
    }

    private static MemorySegment wide(Arena arena, String text) {
        byte[] bytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(bytes.length, 2);
        segment.copyFrom(MemorySegment.ofArray(bytes));
        return segment;
    }

    /** The top-level window titled exactly {@code title}, waited for up to {@code millis}; NULL if none came. */
    static MemorySegment window(String title, long millis) {
        long until = System.currentTimeMillis() + millis;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = wide(arena, title);
            while (true) {
                MemorySegment hwnd = (MemorySegment) FIND_WINDOW.invokeExact(MemorySegment.NULL, name);
                if (hwnd.address() != 0 || System.currentTimeMillis() > until) return hwnd;
                Thread.sleep(50);
            }
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static MemorySegment window(String title) {
        MemorySegment hwnd = window(title, 10_000);
        if (hwnd.address() == 0) throw new AssertionError("no window titled '" + title + "'");
        return hwnd;
    }

    static long style(MemorySegment hwnd) { return windowLong(hwnd, GWL_STYLE); }

    static long exStyle(MemorySegment hwnd) { return windowLong(hwnd, GWL_EXSTYLE); }

    private static long windowLong(MemorySegment hwnd, int index) {
        try {
            return (long) GET_WINDOW_LONG.invokeExact(hwnd, index);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * How much taller the window is than its page, in physical pixels: its title bar and frame as drawn (0 without
     * them). Styles alone do not say: a frameless window keeps its caption style, for Snap and the animations.
     */
    static int frameHeight(MemorySegment hwnd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outer = arena.allocate(16), inner = arena.allocate(16);
            int a = (int) GET_WINDOW_RECT.invokeExact(hwnd, outer);
            int b = (int) GET_CLIENT_RECT.invokeExact(hwnd, inner);
            if (a == 0 || b == 0) throw new AssertionError("no such window");
            int outerHeight = outer.get(JAVA_INT, 12) - outer.get(JAVA_INT, 4);
            int innerHeight = inner.get(JAVA_INT, 12) - inner.get(JAVA_INT, 4);
            return outerHeight - innerHeight;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static final MethodHandle GET_DC = handle("GetDC", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle RELEASE_DC = handle("ReleaseDC", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle GET_PIXEL = LINKER.downcallHandle(
            SymbolLookup.libraryLookup("gdi32", Arena.global()).find("GetPixel").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

    /**
     * The colour of the window's frame as it is on the screen (COLORREF 0x00BBGGRR): a pixel on its visible left edge,
     * halfway down (the frame Windows draws, inside the invisible margins it sizes from). -1 if this Windows cannot say.
     */
    static long frameColorOnScreen(MemorySegment hwnd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bounds = arena.allocate(16);
            int hr = (int) DWM_GET.invokeExact(hwnd, 9, bounds, 16);   // DWMWA_EXTENDED_FRAME_BOUNDS
            if (hr != 0) return -1;
            int left = bounds.get(JAVA_INT, 0), top = bounds.get(JAVA_INT, 4), bottom = bounds.get(JAVA_INT, 12);
            MemorySegment screen = (MemorySegment) GET_DC.invokeExact(MemorySegment.NULL);
            int pixel = (int) GET_PIXEL.invokeExact(screen, left, (top + bottom) / 2);
            int released = (int) RELEASE_DC.invokeExact(MemorySegment.NULL, screen);
            return Integer.toUnsignedLong(pixel);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The colour on the screen at a point (physical pixels; COLORREF 0x00BBGGRR). */
    static long pixelOnScreen(int x, int y) {
        try {
            MemorySegment screen = (MemorySegment) GET_DC.invokeExact(MemorySegment.NULL);
            int pixel = (int) GET_PIXEL.invokeExact(screen, x, y);
            int released = (int) RELEASE_DC.invokeExact(MemorySegment.NULL, screen);
            return Integer.toUnsignedLong(pixel);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** Whether two COLORREFs are the same colour, give or take a little in each channel. */
    static boolean near(long a, long b) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 12) return false;
        }
        return true;
    }

    /** The cursor Windows shows now: its handle. */
    static long shownCursor() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(24 + 8);   // CURSORINFO: cbSize, flags, hCursor, ptScreenPos
            info.set(JAVA_INT, 0, 24);
            int ok = (int) GET_CURSOR_INFO.invokeExact(info);
            if (ok == 0) throw new AssertionError("GetCursorInfo failed");
            return info.get(JAVA_LONG, 8);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** One of the system's cursors: 32512 the arrow, 32513 the I-beam, 32649 the hand, 32646 the four arrows. */
    static long systemCursor(int id) {
        try {
            MemorySegment c = (MemorySegment) LOAD_CURSOR.invokeExact(MemorySegment.NULL, MemorySegment.ofAddress(id));
            return c.address();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The pointer's place on the screen, in physical pixels. */
    static int[] cursor() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(8);
            int ok = (int) GET_CURSOR_POS.invokeExact(point);
            if (ok == 0) throw new AssertionError("GetCursorPos failed");
            return new int[] {point.get(JAVA_INT, 0), point.get(JAVA_INT, 4)};
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** A window's outer bounds, physical pixels: left, top, right, bottom. */
    static int[] rect(MemorySegment hwnd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment r = arena.allocate(16);
            int ok = (int) GET_WINDOW_RECT.invokeExact(hwnd, r);
            if (ok == 0) throw new AssertionError("GetWindowRect failed");
            return new int[] {r.get(JAVA_INT, 0), r.get(JAVA_INT, 4), r.get(JAVA_INT, 8), r.get(JAVA_INT, 12)};
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** What Windows does when a person moves a window: the move begins, the window goes there, the move ends. */
    static void moveAsAPersonWould(MemorySegment hwnd, int x, int y) {
        try {
            long began = (long) SEND_MESSAGE_PLAIN.invokeExact(hwnd, 0x0231, 0L, 0L);   // WM_ENTERSIZEMOVE
            int moved = (int) SET_WINDOW_POS.invokeExact(hwnd, MemorySegment.NULL, x, y, 0, 0, 0x0001 | 0x0004 | 0x0010);
            long ended = (long) SEND_MESSAGE_PLAIN.invokeExact(hwnd, 0x0232, 0L, 0L);   // WM_EXITSIZEMOVE
            if (moved == 0) throw new AssertionError("SetWindowPos failed");
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The window's close button, as a person would click it (WM_CLOSE). */
    static void close(MemorySegment hwnd) {
        try {
            int ok = (int) POST_MESSAGE.invokeExact(hwnd, 0x0010, 0L, 0L);
            if (ok == 0) throw new AssertionError("WM_CLOSE not posted");
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** A person's move of the window beginning (true) or ending, as Windows tells the window. */
    static void moving(MemorySegment hwnd, boolean begins) {
        try {
            long r = (long) SEND_MESSAGE_PLAIN.invokeExact(hwnd, begins ? 0x0231 : 0x0232, 0L, 0L);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The real mouse (it moves the person's pointer: opt-in tests only): to a point, physical pixels. */
    static void mouseTo(int x, int y) {
        try {
            int ok = (int) SET_CURSOR_POS.invokeExact(x, y);
            if (ok == 0) throw new AssertionError("SetCursorPos failed");
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** A key down (true) or up, as the keyboard would: a virtual-key code (0x11 Ctrl, 0x10 Shift, 0x12 Alt). */
    static void key(int virtualKey, boolean down) {
        try {
            KEYBD_EVENT.invokeExact((byte) virtualKey, (byte) 0, down ? 0 : 0x0002, 0L);   // KEYEVENTF_KEYUP
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The left button down (true) or up, where the pointer is. */
    static void mouseButton(boolean down) {
        try {
            MOUSE_EVENT.invokeExact(down ? 0x0002 : 0x0004, 0, 0, 0, 0L);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** A drag with the real mouse, in steps as a hand would, from one screen point to another. */
    static void drag(int fromX, int fromY, int toX, int toY) throws InterruptedException {
        mouseTo(fromX, fromY);
        Thread.sleep(150);
        mouseButton(true);
        Thread.sleep(150);
        for (int i = 1; i <= 20; i++) {
            mouseTo(fromX + (toX - fromX) * i / 20, fromY + (toY - fromY) * i / 20);
            Thread.sleep(25);
        }
        Thread.sleep(150);
        mouseButton(false);
        Thread.sleep(300);
    }

    /** The window in front, the one the keyboard goes to. */
    static MemorySegment foreground() {
        try {
            return (MemorySegment) GET_FOREGROUND.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static boolean visible(MemorySegment hwnd) {
        try {
            return (int) IS_WINDOW_VISIBLE.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** The window's icon as Windows has it (big, else small): 0 when it has none of its own. */
    static long icon(MemorySegment hwnd) {
        try {
            long big = (long) SEND_MESSAGE.invokeExact(hwnd, WM_GETICON, 1L, MemorySegment.NULL);
            return big != 0 ? big : (long) SEND_MESSAGE.invokeExact(hwnd, WM_GETICON, 0L, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Clicks a dialog's button (IDOK, IDCANCEL, or a message box's IDYES 6 / IDNO 7) as a person would. A box with
     * only OK may number it IDCANCEL; without a button of that number, the dialog is told the command instead.
     */
    static void press(MemorySegment dialog, int button) {
        try {
            MemorySegment control = MemorySegment.NULL;
            // the button, once the dialog has made it (a busy machine makes a file dialog's late)
            for (long until = System.currentTimeMillis() + 10_000; System.currentTimeMillis() < until; Thread.sleep(50)) {
                control = (MemorySegment) GET_DLG_ITEM.invokeExact(dialog, button);
                if (control.address() != 0) break;
                if (button == IDOK) {
                    // a message box with only OK numbers it IDCANCEL: that one, only if it is the OK button
                    MemorySegment only = (MemorySegment) GET_DLG_ITEM.invokeExact(dialog, IDCANCEL);
                    if (only.address() != 0 && "OK".equalsIgnoreCase(text(only).replace("&", ""))) {
                        control = only;
                        break;
                    }
                }
            }
            // what a click on it says to the dialog (WM_COMMAND, BN_CLICKED, the button): BM_CLICK itself may fail
            // when the dialog is not the active window (as Microsoft says), as when someone works in another app
            int id = control.address() != 0 ? (int) GET_DLG_CTRL_ID.invokeExact(control) : button;
            int posted = (int) POST_MESSAGE.invokeExact(dialog, WM_COMMAND, (long) (id & 0xFFFF), control.address());
            if (posted == 0) throw new AssertionError("the dialog took no button");
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** Types {@code text} into a file dialog's file name box, as a person would before pressing OK. */
    static void typeFileName(MemorySegment dialog, String text) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment box = null;
            for (long until = System.currentTimeMillis() + 10_000; box == null && System.currentTimeMillis() < until; ) {
                box = fileNameBox(dialog, 0);   // shown once the dialog is drawn
                if (box == null) Thread.sleep(100);
            }
            if (box == null) throw new AssertionError("the dialog has no file name box; its controls:%n%s".formatted(tree(dialog, 0)));
            long set = (long) SEND_MESSAGE.invokeExact(box, WM_SETTEXT, 0L, wide(arena, text));
            if (set == 0) throw new AssertionError("the file name box took no text");
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * The file name box, wherever the dialog keeps it: a direct child in an open dialog, deeper in a save dialog
     * (under its DirectUI panes). The first visible control numbered as a file name box is.
     */
    private static MemorySegment fileNameBox(MemorySegment parent, int depth) throws Throwable {
        if (depth > 8) return null;
        MemorySegment child = MemorySegment.NULL;
        while (true) {
            child = (MemorySegment) FIND_WINDOW_EX.invokeExact(parent, child, MemorySegment.NULL, MemorySegment.NULL);
            if (child.address() == 0) return null;
            if (!visible(child)) continue;
            int id = (int) GET_DLG_CTRL_ID.invokeExact(child);
            String cls = className(child);
            boolean box = cls.equals("ComboBoxEx32") || cls.equals("ComboBox") || cls.equals("Edit");
            if (box && (id == FILE_NAME_COMBO || id == FILE_NAME_SAVE || id == FILE_NAME_EDIT)) return child;
            MemorySegment inside = fileNameBox(child, depth + 1);
            if (inside != null) return inside;
        }
    }

    /** A control's text (a button's caption). */
    static String text(MemorySegment hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = arena.allocate(512);
            int length = (int) GET_WINDOW_TEXT.invokeExact(hwnd, text, 256);
            return new String(text.asSlice(0, length * 2L).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE);
        }
    }

    private static String className(MemorySegment hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocate(512);
            int length = (int) GET_CLASS_NAME.invokeExact(hwnd, name, 256);
            return new String(name.asSlice(0, length * 2L).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE);
        }
    }

    /** A window's controls, one per line: class, number, shown or not (what a failed search saw). */
    static String tree(MemorySegment parent, int depth) throws Throwable {
        StringBuilder out = new StringBuilder();
        MemorySegment child = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            while (depth < 10) {
                child = (MemorySegment) FIND_WINDOW_EX.invokeExact(parent, child, MemorySegment.NULL, MemorySegment.NULL);
                if (child.address() == 0) break;
                String cls = className(child);
                int id = (int) GET_DLG_CTRL_ID.invokeExact(child);
                MemorySegment r = arena.allocate(16);
                int got = (int) GET_WINDOW_RECT.invokeExact(child, r);
                out.append("  ".repeat(depth)).append(cls).append(" 0x").append(Integer.toHexString(id)).append(visible(child) ? "" : " (hidden)")
                        .append(" [").append(r.get(JAVA_INT, 0)).append(",").append(r.get(JAVA_INT, 4)).append(" ").append(r.get(JAVA_INT, 8) - r.get(JAVA_INT, 0))
                        .append("x").append(r.get(JAVA_INT, 12) - r.get(JAVA_INT, 4)).append("]").append(System.lineSeparator());
                out.append(tree(child, depth + 1));
            }
        }
        return out.toString();
    }

    /**
     * The dialog titled {@code title}, once it is shown: a path typed into it (if any), then {@code button}; again if it
     * is still there a moment later (a busy machine draws it late, and the first click finds it not ready), as a
     * person would click again.
     */
    static void answer(String title, String fileName, int button) {
        MemorySegment dialog = window(title);
        for (int attempt = 0; attempt < 3; attempt++) {
            // typed once: pressed again, a folder dialog chooses the folder the first press opened (as a person does)
            if (fileName != null && attempt == 0) typeFileName(dialog, fileName);
            try {
                Thread.sleep(400 + attempt * 600L);   // drawn and ready for the click
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            press(dialog, button);
            // answered once it closes: wait for that, else try again
            long until = System.currentTimeMillis() + 3000 + attempt * 2000L;
            while (System.currentTimeMillis() < until && window(title, 0).address() != 0) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            if (window(title, 0).address() == 0) return;
        }
    }
}
