package org.embulk.input.postgresql;

import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.embulk.spi.Exec;
import org.embulk.input.jdbc.JdbcInputConnection;
import org.embulk.input.jdbc.JdbcLiteral;
import org.embulk.input.jdbc.getter.ColumnGetter;

public class PostgreSQLInputConnection
        extends JdbcInputConnection
{
    private final Logger logger = Exec.getLogger(PostgreSQLInputConnection.class);

    public PostgreSQLInputConnection(Connection connection, String schemaName)
            throws SQLException
    {
        super(connection, schemaName);
    }

    @Override
    protected BatchSelect newBatchSelect(String select,
            List<JdbcLiteral> parameters, List<ColumnGetter> getters,
            int fetchRows, int queryTimeout) throws SQLException
    {
        String sql = "DECLARE cur NO SCROLL CURSOR FOR " + select;

        logger.info("SQL: " + sql);
        PreparedStatement stmt = connection.prepareStatement(sql);
        try {
            if (!parameters.isEmpty()) {
                logger.info("Parameters: {}", parameters);
                prepareParameters(stmt, getters, parameters);
            }
            stmt.executeUpdate();
        } finally {
            stmt.close();
        }

        String fetchSql = "FETCH FORWARD "+fetchRows+" FROM cur";
        // Because socketTimeout is set in Connection, don't need to set quertyTimeout.
        return new CursorSelect(fetchSql, connection.prepareStatement(fetchSql));
    }

    public class CursorSelect
            implements BatchSelect
    {
        private final String fetchSql;
        private final PreparedStatement fetchStatement;

        public CursorSelect(String fetchSql, PreparedStatement fetchStatement) throws SQLException
        {
            this.fetchSql = fetchSql;
            this.fetchStatement = fetchStatement;
        }

        public ResultSet fetch() throws SQLException
        {
            logger.info("SQL: " + fetchSql);
            long startTime = System.currentTimeMillis();

            ResultSet rs = fetchStatement.executeQuery();

            double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
            logger.info(String.format("> %.2f seconds", seconds));
            return rs;
        }

        public void close() throws SQLException
        {
            // TODO close?
        }
    }
}
