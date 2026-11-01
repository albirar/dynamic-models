package cat.albirar.dynmod.filtering.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable set of selected {@link ModelPart sections}, parsed once per request from the
 * concatenated codes sent by the consumer (for example {@code "BP"}).
 * <p>
 * The {@link #full() full} selection represents a request without selection: every section
 * and every property, including the unmarked ones, is part of the response. It is not
 * equivalent to a selection with every code, which still omits unmarked properties.
 */
public final class ModelSelection {

    private static final ModelSelection FULL = new ModelSelection(EnumSet.allOf(ModelPart.class), true);

    private final Set<ModelPart> parts;
    private final boolean full;

    private ModelSelection(Set<ModelPart> parts, boolean full) {
        this.parts = Collections.unmodifiableSet(parts);
        this.full = full;
    }

    /**
     * @return the selection of a request without selection: the complete model
     */
    public static ModelSelection full() {
        return FULL;
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
        return new ModelSelection(parts, false);
    }

    /**
     * @return {@code true} if this is the {@link #full() full} selection
     */
    public boolean isFull() {
        return full;
    }

    /**
     * @return {@code true} if the section is selected; always {@code true} for the full selection
     */
    public boolean includes(ModelPart part) {
        return parts.contains(part);
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

    /**
     * Checks a nested section together with its ancestors, applying the same cascade as the
     * serialization: {@code includesAll(MARKET, ECONOMIC)} is {@code true} only when market
     * data and economic data are both selected.
     *
     * @return {@code true} if every part is selected
     */
    public boolean includesAll(ModelPart... path) {
        for (ModelPart part : path) {
            if (!parts.contains(part)) {
                return false;
            }
        }
        return true;
    }

    public Set<ModelPart> parts() {
        return parts;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModelSelection selection && full == selection.full && parts.equals(selection.parts);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(full) * 31 + parts.hashCode();
    }

    @Override
    public String toString() {
        return full ? "ModelSelection[FULL]" : "ModelSelection" + parts;
    }
}
