package cat.albirar.dynmod.filtering.model;

/**
 * Thrown when a selection value contains a code that does not match any {@link ModelPart}.
 */
@SuppressWarnings("serial")
public class InvalidModelSelectionException extends IllegalArgumentException {

    public InvalidModelSelectionException(String message) {
        super(message);
    }
}
