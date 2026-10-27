package cat.albirar.dynmod.filtering.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable set of selected {@link ModelPart sections}, parsed once per response from the
 * concatenated codes sent by the consumer (for example {@code "BP"}).
 */
public final class ModelSelection {

    private final Set<ModelPart> parts;

    private ModelSelection(Set<ModelPart> parts) {
        this.parts = Collections.unmodifiableSet(parts);
    }

    /**
     * Parses a selection value. Each character must be the code of a section; repeated codes
     * are deduplicated and an empty value means no optional section.
     *
     * @param value the concatenated codes
     * @return the selection
     * @throws InvalidModelSelectionException if a character is not a known code
     */
    public static ModelSelection parse(String value) {
        Objects.requireNonNull(value, "value");
        EnumSet<ModelPart> parts = EnumSet.noneOf(ModelPart.class);
        for (int i = 0; i < value.length(); i++) {
            char code = value.charAt(i);
            parts.add(ModelPart.fromCode(code).orElseThrow(() -> new InvalidModelSelectionException(
                    "Unknown model section code '" + code + "' in '" + value + "'")));
        }
        return new ModelSelection(parts);
    }

    /**
     * @return {@code true} if any of the candidates is selected
     */
    public boolean includesAny(ModelPart[] candidates) {
        for (ModelPart candidate : candidates) {
            if (parts.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public Set<ModelPart> parts() {
        return parts;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelSelection selection && parts.equals(selection.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public String toString() {
        return "ModelSelection" + parts;
    }
}
