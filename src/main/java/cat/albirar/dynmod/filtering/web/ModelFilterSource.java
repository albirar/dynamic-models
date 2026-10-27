package cat.albirar.dynmod.filtering.web;

import lombok.Getter;

/**
 * Where the model selection is read from. Each source is exclusive: there is no fallback.
 */
@Getter
public enum ModelFilterSource {
    QUERY_PARAM("m"),
    HEADER("X-Model-Sections");

    private final String defaultName;

    ModelFilterSource(String defaultName) {
        this.defaultName = defaultName;
    }
}
