package org.kraweki.lib.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface StatementInitializer {
    void setVariables(PreparedStatement statement) throws SQLException;
}
