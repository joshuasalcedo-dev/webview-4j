package io.joshuasalcedo.swaswa.component;

/**
 * Code (HTML's {@code <code>} and {@code <pre>}): a code span, a code block, a code editor.
 * Every library's is one (<code>, <pre> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface CodeView<Self extends CodeView<Self>> extends Component<Self> {}
