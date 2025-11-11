package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList;
import com.capco.brsp.synthesisengine.utils.Utils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service(value = "agensGraphService")
public class AgensGraphService{

    private JdbcTemplate jdbcTemplate;
    private SingleConnectionDataSource dataSource;

    public void connect() throws SQLException {
        if (jdbcTemplate != null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("user", Utils.getEnvVariable("AGENSGRAPH_USER"));
        properties.setProperty("password", Utils.getEnvVariable("AGENSGRAPH_PASS"));

        String host = Utils.getEnvVariable("AGENSGRAPH_HOST");
        Connection connection = DriverManager.getConnection(host, properties);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET graph_path = " + Utils.getEnvVariable("AGENSGRAPH_GRAPHPATH"));
        }

        this.dataSource = new SingleConnectionDataSource(connection, false);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<List<Map<String, Object>>> executeListCypher(ConcurrentLinkedList<Object> listCypher) {
        List<List<Map<String, Object>>> listResults = new ConcurrentLinkedList<>();

        listCypher.forEach(e -> {
            try {
                if (e instanceof Map<?,?> map) {
                    Object cypherObj = map.get("statement");
                    if (cypherObj instanceof String cypherQuery) {
                        listResults.add(executeCypher(cypherQuery));
                    }
                }
                if (e instanceof String cypherQuery) {
                    listResults.add(executeCypher(cypherQuery));
                }
            } catch (DataAccessException ex) {
                throw new RuntimeException(ex);
            }
        });

        return listResults;
    }

    public List<Map<String, Object>> executeCypher(String cypherQuery) throws DataAccessException {
        return jdbcTemplate.queryForList(cypherQuery);
    }

    public void executeCyphersNoReturn(String cypherQuery) throws DataAccessException {
         jdbcTemplate.execute(cypherQuery);
    }

    public void close() throws SQLException {
        if (dataSource != null) {
            try {
                Connection conn = dataSource.getConnection();
                if (!conn.isClosed()) {
                    conn.close();
                }
                dataSource.destroy();
            } catch (SQLException e) {
                throw new SQLException("Error while closing the connection to AgensGraph: " + e.getMessage());
            } finally {
                jdbcTemplate = null;
                dataSource = null;
            }
        }
    }
}
