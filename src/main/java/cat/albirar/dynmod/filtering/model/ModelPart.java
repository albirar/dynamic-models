package cat.albirar.dynmod.filtering.model;

import java.util.Optional;

import lombok.Getter;

/**
 * Optional sections of a model. Each section has a stable one-character code used by
 * REST consumers to select it.
 */
@Getter
public enum ModelPart {
    BRAND('B', "Brand information"),
    MARKET('M', "Market data"),
    PARTS('P', "Vehicle parts"),
    ECONOMIC('E', "Economic data");

    private final char code;
    private final String description;

    ModelPart(char code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the section with the given code. Codes are case-sensitive.
     *
     * @param code the code to look up
     * @return the section, or empty if no section has this code
     */
    public static Optional<ModelPart> fromCode(char code) {
        for (ModelPart part : values()) {
            if (part.code == code) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }
}
