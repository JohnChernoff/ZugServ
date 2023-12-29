package org.kraweki.lib.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@FunctionalInterface
public interface ResultSetMapper<R> {
    Optional<R> map(ResultSet resultSet) throws SQLException;
}
