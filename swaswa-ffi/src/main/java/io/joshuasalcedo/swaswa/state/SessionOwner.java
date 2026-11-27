package io.joshuasalcedo.swaswa.state;

/**
 * What a session belongs to: the root of a page's components ({@code SwaApplication}). The {@link Context} of a
 * component goes up its parents to one, and ends at its session.
 */
public interface SessionOwner {

    Session session();
}
