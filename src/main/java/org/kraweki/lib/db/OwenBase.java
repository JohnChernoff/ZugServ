package org.kraweki.lib.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OwenBase {

    public static boolean closeQueries = true;
    private static final Logger logger = Logger.getLogger(OwenBase.class.getName());
    public static class SqlQuery {
        private final String statement;
        private final PreparedStatement preparedStatement;
        private final ResultSet resultSet;
        private final Connection conn;

        public SqlQuery(final String statement, final Connection conn) {
            this.statement = statement;
            this.conn = conn;
            this.preparedStatement = null;
            this.resultSet = null;
        }

        private SqlQuery(final String statement, final PreparedStatement preparedStatement, final ResultSet resultSet,
                         final Connection conn) {
            this.statement = statement;
            this.resultSet = resultSet;
            this.preparedStatement = preparedStatement;
            this.conn = conn;
        }

        private Optional<SqlQuery> withResultSet(final StatementInitializer init) {
            return prepareStatement(init).flatMap(s ->
                    runQuery(s).flatMap(q ->
                            Optional.of(new SqlQuery(this.statement, s, q, this.conn))));
        }

        private Optional<ResultSet> getResultSet() {
            return Optional.ofNullable(this.resultSet);
        }

        public <R> Optional<R> mapResultSet(final StatementInitializer init, final ResultSetMapper<R> mapper) {
            return withResultSet(init).flatMap(it -> it.getResultSet().flatMap(rs -> {
                try {
                    return mapper.map(rs);
                } catch (SQLException ex) {
                    logSQLException(ex);
                    return Optional.empty();
                } finally {
                    it.cleanup();
                }
            }));
        }

        private Optional<PreparedStatement> prepareStatement(final StatementInitializer init) {
            try {
                final PreparedStatement preparedStatement = conn.prepareStatement(statement);
                init.setVariables(preparedStatement);
                return Optional.of(preparedStatement);
            } catch (SQLException e) {
                logSQLException(e);
                cleanup();
                return Optional.empty();
            }
        }

        private Optional<ResultSet> runQuery(final PreparedStatement statement) {
            try {
                return Optional.of(statement.executeQuery());
            } catch (SQLException e) {
                logSQLException(e);
                cleanup();
                return Optional.empty();
            }
        }

        public void runUpdate(final StatementInitializer varSetter, final Consumer<SQLException> whenFails) {
            try (final PreparedStatement preparedStatement = conn.prepareStatement(statement)) {
                varSetter.setVariables(preparedStatement);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                whenFails.accept(e);
            }
        }

        public void runUpdate(final StatementInitializer varSetter) {
            runUpdate(varSetter, OwenBase::logSQLException);
        }

        public void runUpdate() {
            runUpdate(it -> {}, OwenBase::logSQLException);
        }

        private void cleanup() {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    logSQLException(e);
                }
            }
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    logSQLException(e);
                }
            }
            if (closeQueries) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    logSQLException(e);
                }
            }
        }
    }

    private final HikariDataSource dataSource;

    public OwenBase(String uri, String usr, String pwd, String db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + uri + "/" + db);
        config.setUsername(usr);
        config.setPassword(pwd);
        config.setMaximumPoolSize(10);  // Adjust based on your needs
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);  // 30 seconds
        config.setIdleTimeout(600000);       // 10 minutes
        config.setMaxLifetime(1800000);      // 30 minutes

        this.dataSource = new HikariDataSource(config);
    }

    public Optional<SqlQuery> makeQuery(final String queryStr) {
        try {
            Connection conn = dataSource.getConnection();
            return Optional.of(new SqlQuery(queryStr, conn));
        } catch (SQLException e) {
            logSQLException(e);
            return Optional.empty();
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private static void logSQLException(SQLException e) {
        logger.log(Level.SEVERE, "SQLException: " + e.getMessage());
        logger.log(Level.SEVERE, "SQLState: " + e.getSQLState());
        logger.log(Level.SEVERE, "VendorError: " + e.getErrorCode());
    }
}
