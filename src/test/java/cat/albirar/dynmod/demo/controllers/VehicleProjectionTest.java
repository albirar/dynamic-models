package cat.albirar.dynmod.demo.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.service.VehicleService;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import jakarta.persistence.EntityManagerFactory;

/**
 * The selection is turned into a projection: only the tables and columns of the selected
 * sections are read, with a constant number of queries. Open Session in View is disabled, so
 * an access to an association that was not loaded would fail the request.
 */
@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:vehicleProjectionTest",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=cat.albirar.dynmod.demo.controllers.SqlStatementRecorder" })
@AutoConfigureMockMvc
class VehicleProjectionTest {

    // A single page with every vehicle: Spring Data skips the count query
    private static final String VEHICLES = "/vehicles?size=100";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private VehicleService vehicleService;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        SqlStatementRecorder.STATEMENTS.clear();
    }

    @ParameterizedTest(name = "''{0}'' -> {1} queries")
    @CsvSource({
            "'&m=',     1",
            "'&m=E',    1",
            "'&m=B',    1",
            "'&m=BE',   1",
            "'&m=M',    2",
            "'&m=P',    2",
            "'&m=PE',   2",
            "'&m=BM',   2",
            "'&m=MP',   3",
            "'&m=BMPE', 3",
            "'',        3" })
    void loadsOnlySelectedSections(String selection, long queries) throws Exception {
        mvc.perform(get(VEHICLES + selection))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(25));
        assertEquals(queries, statistics.getPrepareStatementCount());
    }

    @Test
    void basicSelectionReadsOnlyTheVehiclesTable() throws Exception {
        mvc.perform(get(VEHICLES + "&m=")).andExpect(status().isOk());
        assertEquals(1, SqlStatementRecorder.STATEMENTS.size());
        String sql = SqlStatementRecorder.STATEMENTS.get(0);
        assertTrue(sql.contains("from vehicles"), sql);
        assertFalse(sql.contains("brands"), sql);
    }

    @Test
    void brandIsJoinedIntoTheVehiclesQuery() throws Exception {
        mvc.perform(get(VEHICLES + "&m=B")).andExpect(status().isOk());
        assertEquals(1, SqlStatementRecorder.STATEMENTS.size());
        assertTrue(SqlStatementRecorder.STATEMENTS.get(0).contains("join brands"));
    }

    @Test
    void economicColumnsAreReadOnlyWhenSelected() throws Exception {
        mvc.perform(get(VEHICLES + "&m=MP")).andExpect(status().isOk());
        assertTrue(SqlStatementRecorder.STATEMENTS.stream().anyMatch(sql -> sql.contains("vehicle_market_data")));
        assertTrue(SqlStatementRecorder.STATEMENTS.stream().anyMatch(sql -> sql.contains("vehicle_parts")));
        SqlStatementRecorder.STATEMENTS.forEach(sql -> {
            assertFalse(sql.contains("currency"), sql);
            assertFalse(sql.contains("market_price"), sql);
            assertFalse(sql.contains("margin"), sql);
        });

        SqlStatementRecorder.STATEMENTS.clear();
        mvc.perform(get(VEHICLES + "&m=ME")).andExpect(status().isOk());
        assertTrue(SqlStatementRecorder.STATEMENTS.stream().anyMatch(sql -> sql.contains("currency")));
    }

    @Test
    void economicDataOfUnselectedParentsIsNotRead() throws Exception {
        mvc.perform(get(VEHICLES + "&m=PE")).andExpect(status().isOk());
        SqlStatementRecorder.STATEMENTS.forEach(sql -> assertFalse(sql.contains("vehicle_market_data"), sql));
        assertTrue(SqlStatementRecorder.STATEMENTS.stream().anyMatch(sql -> sql.contains("margin")));
    }

    @ParameterizedTest
    @CsvSource({ "'&m=BX'", "'&m=b'", "'&m=B&m=P'" })
    void invalidSelectionDoesNotQuery(String selection) throws Exception {
        mvc.perform(get(VEHICLES + selection)).andExpect(status().isBadRequest());
        assertEquals(0, statistics.getPrepareStatementCount());
    }

    @ParameterizedTest
    @CsvSource({ "'&m=B'", "'&m=BMPE'", "''" })
    void filtersOfOtherAssociationsStillApply(String selection) throws Exception {
        // The search joins brand and market data whether they are selected or not
        mvc.perform(get("/vehicles?brandManufacturerWMI=TOY&commercialNamePattern=Corolla" + selection))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].model").value("Corolla"));
    }

    @Test
    void detachedEntitiesWithoutAssociationsCanBeComparedAndPrinted() {
        List<VehicleEntity> vehicles = vehicleService
                .searchVehicles(new VehicleSearchCriteria(), ModelSelection.parse(""), PageRequest.of(0, 5))
                .getContent();
        VehicleEntity first = vehicles.get(0);

        // Outside the session: none of these may initialize an association
        assertTrue(first.toString().contains("model=" + first.getModel()));
        Set<VehicleEntity> set = new HashSet<>(vehicles);
        assertEquals(5, set.size());
        assertTrue(set.contains(first));
        assertFalse(first.equals(vehicles.get(1)));
        // The guardrail is still there
        assertThrows(LazyInitializationException.class, () -> first.getParts().size());
    }
}
