package cat.albirar.dynmod.filtering.jpa;

import java.util.List;

/**
 * Thrown when a {@link ProjectionPlan} does not match the JPA metamodel of its entity. Lists
 * every problem found, not only the first one.
 */
@SuppressWarnings("serial")
public class InvalidProjectionPlanException extends IllegalStateException {

    private final List<String> problems;

    public InvalidProjectionPlanException(Class<?> type, List<String> problems) {
        super("Invalid projection plan for " + type.getName() + ":\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
