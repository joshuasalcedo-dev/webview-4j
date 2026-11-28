package io.joshuasalcedo.swaswa.component;

/**
 * Fields filled in and sent together (HTML's {@code <form>}): a sign-in form, a questionnaire.
 * Every library's is one (<form> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface FormBase<Self extends FormBase<Self>> extends Component<Self> {}
