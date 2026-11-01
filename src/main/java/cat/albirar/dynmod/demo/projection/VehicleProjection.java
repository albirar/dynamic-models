package cat.albirar.dynmod.demo.projection;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.models.MarketDataBean;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.models.VehiclePartBean;
import cat.albirar.dynmod.demo.models.VehicleType;
import cat.albirar.dynmod.filtering.jpa.ProjectedRow;
import cat.albirar.dynmod.filtering.jpa.ProjectionExecutor;
import cat.albirar.dynmod.filtering.jpa.ProjectionPlan;

/**
 * Builds {@link VehicleModelBean} from the values projected for a vehicle. The plan is derived
 * from the annotations of the beans with {@link #plan(ProjectionExecutor)}, so the columns read
 * for each section are always those of the serialization.
 */
public final class VehicleProjection {

    private VehicleProjection() {
    }

    /**
     * @return the projection plan of {@link VehicleModelBean}, derived once and validated
     */
    public static ProjectionPlan<VehicleEntity> plan(ProjectionExecutor executor) {
        return executor.planFor(VehicleModelBean.class, VehicleEntity.class);
    }

    /**
     * Builds the bean from the projected values. Properties that were not projected keep their
     * default value; the serialization filter omits them from the response.
     */
    public static VehicleModelBean toBean(ProjectedRow row) {
        VehicleModelBean target = new VehicleModelBean();
        target.setLine(Optional.ofNullable(row.get("line")));
        target.setModel(row.get("model"));
        VehicleTypeEntity type = row.get("type");
        target.setType(type != null ? VehicleType.valueOf(type.name()) : null);
        target.setInceptionYear(row.<Integer>get("inceptionYear"));
        target.setConstruction(row.<Integer>get("constructionYear"));
        target.setCommercialization(Optional.ofNullable(row.<Instant>get("commercialization")));
        target.setRetirement(Optional.ofNullable(row.<Instant>get("retirement")));

        if (row.has("brand.manufacturerWMI")) {
            BrandBean brand = new BrandBean();
            brand.setManufacturerWMI(row.get("brand.manufacturerWMI"));
            brand.setCommercialBrandName(row.get("brand.commercialBrandName"));
            brand.setManufacturer(row.get("brand.manufacturer"));
            brand.setCountry(row.get("brand.country"));
            target.setBrand(brand);
        }
        target.setMarketData(toMap(row, "marketData", element -> element.get("country"), VehicleProjection::toMarketData));
        target.setParts(toMap(row, "parts", element -> element.get("partNumber"), VehicleProjection::toPart));
        return target;
    }

    private static MarketDataBean toMarketData(ProjectedRow row) {
        MarketDataBean target = new MarketDataBean();
        target.setCountry(row.get("country"));
        target.setStartCommercializing(row.get("startCommercializing"));
        target.setEndCommercializing(row.get("endCommercializing"));
        target.setCommercialName(row.get("commercialName"));
        if (row.has("currency")) {
            String currency = row.get("currency");
            target.setCurrency(currency != null ? Currency.getInstance(currency) : null);
            target.setLocalMarketPrice(row.<Float>get("localMarketPrice"));
            target.setMarketPrice(row.<Float>get("marketPrice"));
            target.setMargin(row.<Float>get("margin"));
        }
        return target;
    }

    private static VehiclePartBean toPart(ProjectedRow row) {
        VehiclePartBean target = new VehiclePartBean();
        target.setPartNumber(row.get("partNumber"));
        target.setName(row.get("name"));
        target.setMountingReference(row.get("mountingReference"));
        if (row.has("marketPrice")) {
            target.setMarketPrice(row.<Float>get("marketPrice"));
            target.setMargin(row.<Float>get("margin"));
        }
        return target;
    }

    /**
     * An absent or empty collection is left {@code null}, as the entity converter does.
     */
    private static <V> Map<String, V> toMap(ProjectedRow row, String attribute, Function<ProjectedRow, String> key,
            Function<ProjectedRow, V> mapper) {
        if (!row.hasCollection(attribute)) {
            return null;
        }
        List<ProjectedRow> elements = row.collection(attribute);
        return elements.isEmpty() ? null : elements.stream().collect(Collectors.toMap(key, mapper));
    }
}
