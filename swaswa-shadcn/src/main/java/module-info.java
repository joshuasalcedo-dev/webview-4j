/**
 * swaswa-shadcn: shadcn/ui's components from Java, typed: {@code new Button("Save")}, {@code new Card(...)},
 * {@code new Switch()}, drawn by React in the page (swaswa-react), styled by their own Tailwind CSS (in the jar).
 */
module io.joshuasalcedo.swaswa.shadcn {
    requires transitive io.joshuasalcedo.swaswa.react;
    exports io.joshuasalcedo.swaswa.shadcn;
}
