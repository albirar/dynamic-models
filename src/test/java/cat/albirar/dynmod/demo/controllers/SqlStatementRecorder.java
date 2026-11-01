package cat.albirar.dynmod.demo.controllers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Records the SQL sent by Hibernate, to check which tables and columns a selection reads.
 */
@SuppressWarnings("serial")
public class SqlStatementRecorder implements StatementInspector {

    static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql.toLowerCase());
        return sql;
    }
}
