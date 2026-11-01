package cat.albirar.dynmod.filtering.jpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import cat.albirar.dynmod.filtering.jpa.ProjectionPlan.CollectionPlan;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

/**
 * Runs a {@link ProjectionPlan} with the JPA Criteria API, reading only the columns of the
 * selected sections. Entities are never loaded: the result is a page of {@link ProjectedRow}.
 * <p>
 * Queries per page: one for the root columns (many-to-one sections are joined into it), one
 * per selected collection for the whole page, and the count query when Spring Data needs it.
 * <p>
 * Plans are checked against the JPA metamodel before their first use. Calling
 * {@link #validate(ProjectionPlan)} when the application starts moves that check, and any
 * error in a plan, to startup.
 */
public class ProjectionExecutor {

    private final EntityManager entityManager;
    private final Set<ProjectionPlan<?>> validPlans = ConcurrentHashMap.newKeySet();
    private final Map<List<Class<?>>, ProjectionPlan<?>> modelPlans = new ConcurrentHashMap<>();

    public ProjectionExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Returns the plan derived from the annotations of a model (see
     * {@link ProjectionPlan#fromModel}), validated. Each plan is derived once.
     *
     * @param model the response model
     * @param type the entity the model is read from
     * @throws InvalidProjectionPlanException if the model cannot be mapped to the entity
     */
    @SuppressWarnings("unchecked")
    public <T> ProjectionPlan<T> planFor(Class<?> model, Class<T> type) {
        return (ProjectionPlan<T>) modelPlans.computeIfAbsent(List.of(model, type), key -> {
            ProjectionPlan<T> plan = ProjectionPlan.fromModel(entityManager.getMetamodel(), model, type);
            validate(plan);
            return plan;
        });
    }

    /**
     * Checks a plan against the JPA metamodel. Each plan is checked once.
     *
     * @throws InvalidProjectionPlanException listing every problem of the plan
     */
    public void validate(ProjectionPlan<?> plan) {
        if (!validPlans.contains(plan)) {
            ProjectionPlanValidator.validate(entityManager.getMetamodel(), plan);
            validPlans.add(plan);
        }
    }

    /**
     * @param plan the projection plan of the resource
     * @param specification the search filters, applied as in {@code JpaSpecificationExecutor}
     * @param selection the selection; {@link ModelSelection#full()} reads every column
     * @param pageable the page and sort; sort properties must be root or many-to-one paths
     * @return the projected rows of the page, in query order
     * @throws InvalidProjectionPlanException if the plan does not match the metamodel
     */
    public <T> Page<ProjectedRow> findAll(ProjectionPlan<T> plan, Specification<T> specification,
            ModelSelection selection, Pageable pageable) {

        validate(plan);
        Map<Object, ProjectedRow> rows = findRoots(plan, specification, selection, pageable);
        if (!rows.isEmpty()) {
            for (CollectionPlan collection : plan.collections()) {
                if (selection.includes(collection.part())) {
                    findCollection(plan, collection, selection, rows);
                }
            }
        }
        return PageableExecutionUtils.getPage(new ArrayList<>(rows.values()), pageable,
                () -> count(plan, specification));
    }

    private <T> Map<Object, ProjectedRow> findRoots(ProjectionPlan<T> plan, Specification<T> specification,
            ModelSelection selection, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> root = query.from(plan.type());
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        List<String> paths = new ArrayList<>();
        paths.add(plan.idPath());
        paths.addAll(plan.columns().select(selection));
        PathResolver resolver = new PathResolver(root);
        List<Selection<?>> selections = new ArrayList<>(paths.stream().map(resolver::get).toList());

        List<Order> orders = pageable.getSort().isSorted() ? QueryUtils.toOrders(pageable.getSort(), root, cb) : List.of();
        // With DISTINCT (some filters join collections) ordered expressions must be selected
        orders.forEach(order -> selections.add(order.getExpression()));
        query.select(cb.tuple(selections)).orderBy(orders);

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(query);
        if (pageable.isPaged()) {
            typedQuery.setFirstResult(Math.toIntExact(pageable.getOffset()));
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        Map<Object, ProjectedRow> rows = new LinkedHashMap<>();
        for (Tuple tuple : typedQuery.getResultList()) {
            rows.putIfAbsent(tuple.get(0), new ProjectedRow(values(paths, tuple, 0)));
        }
        return rows;
    }

    private <T> void findCollection(ProjectionPlan<T> plan, CollectionPlan collection, ModelSelection selection,
            Map<Object, ProjectedRow> rows) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<T> owner = query.from(plan.type());
        Join<T, ?> element = owner.join(collection.attribute());
        Path<Object> ownerId = owner.get(plan.idPath());

        List<String> paths = collection.columns().select(selection, collection.part());
        PathResolver resolver = new PathResolver(element);
        List<Selection<?>> selections = new ArrayList<>();
        selections.add(ownerId);
        paths.forEach(path -> selections.add(resolver.get(path)));
        query.select(cb.tuple(selections)).where(ownerId.in(rows.keySet()));

        rows.values().forEach(row -> row.initCollection(collection.attribute()));
        for (Tuple tuple : entityManager.createQuery(query).getResultList()) {
            rows.get(tuple.get(0)).addElement(collection.attribute(), new ProjectedRow(values(paths, tuple, 1)));
        }
    }

    private <T> long count(ProjectionPlan<T> plan, Specification<T> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(plan.type());
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        // Same rule as Spring Data: filters that join collections make the query distinct
        query.select(query.isDistinct() ? cb.countDistinct(root) : cb.count(root));
        return entityManager.createQuery(query).getSingleResult();
    }

    private static Map<String, Object> values(List<String> paths, Tuple tuple, int offset) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < paths.size(); i++) {
            values.put(paths.get(i), tuple.get(i + offset));
        }
        return values;
    }

    /**
     * Resolves dotted paths from a root or join, creating one left join per many-to-one
     * association so that a null association does not remove the row.
     */
    private static final class PathResolver {

        private final From<?, ?> from;
        private final Map<String, From<?, ?>> joins = new HashMap<>();

        PathResolver(From<?, ?> from) {
            this.from = from;
        }

        Path<Object> get(String path) {
            int dot = path.lastIndexOf('.');
            if (dot < 0) {
                return from.get(path);
            }
            return join(path.substring(0, dot)).get(path.substring(dot + 1));
        }

        private From<?, ?> join(String associationPath) {
            From<?, ?> join = joins.get(associationPath);
            if (join == null) {
                int dot = associationPath.lastIndexOf('.');
                From<?, ?> parent = dot < 0 ? from : join(associationPath.substring(0, dot));
                join = parent.join(associationPath.substring(dot + 1), JoinType.LEFT);
                joins.put(associationPath, join);
            }
            return join;
        }
    }
}
