/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import static com.softwareplumbers.common.sql.AbstractDatabase.defaultValueFormatter;
import com.softwareplumbers.common.sql.DatabaseConfig;
import com.softwareplumbers.common.sql.DatabaseConfigFactory;
import com.softwareplumbers.common.sql.Schema;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.sql.DataSource;
/**
 *
 * @author jonathan
 */
public class MessageDatabase extends AbstractDatabase<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template, DatabaseInterface> {

    private static DataSource getDatasource(URI jdbcURI, Properties properties) throws SQLException {
        HikariConfig config = new HikariConfig();      
        config.setDataSourceProperties(properties);
        config.setJdbcUrl(jdbcURI.toString());
        config.setUsername(properties.getProperty("username"));
        config.setPassword(properties.getProperty("password"));
        if (properties.containsKey("driverClassName")) config.setDriverClassName(properties.getProperty("driverClassName"));
        return new HikariDataSource(config);
    }
    
    public MessageDatabase(DataSource datasource, DatabaseConfig<EntityType, DataType, Operation, Template> config) {
        super(datasource, config);
    }

    public MessageDatabase(DataSource datasource, DatabaseConfigFactory<EntityType, DataType, Operation, Template> config, CreateOption createOption) throws SQLException {
        super(datasource, config, createOption);
    }
    
    public MessageDatabase(URI jdbcURI, Properties properties, DatabaseConfig<EntityType, DataType, Operation, Template> config) throws SQLException {
        super(getDatasource(jdbcURI, properties), config);
    }

    public MessageDatabase(URI jdbcURI, Properties properties, DatabaseConfigFactory<EntityType, DataType, Operation, Template> config, CreateOption createOption) throws SQLException {
        super(getDatasource(jdbcURI, properties), config, createOption);
    }    
    
    public MessageDatabase() {
        super();
    }

    @Override
    public DatabaseInterface createInterface() throws SQLException {
        return new DatabaseInterface(this);
    }
  
    public enum EntityType {
        MESSAGE,
        FEED, 
        REMOTE_MESSAGE,
        SELF,
        NODE
    }
    
    public static enum DataType {
        STRING,
        UUID,
        NUMBER,
        BOOLEAN,
        BINARY
    }    
    
    public enum Operation {
        CREATE_FEED,
        GET_FEED_BY_ID,
        GET_FEED_BY_ID_AND_NAME,
        CREATE_MESSAGE,
        GET_MESSAGES_BY_ID,
        GENERATE_TIMESTAMP,
        GET_NEW_MESSAGES,
        CREATE_NODE,
        CREATE_SELF,
        GET_NODE,
        GET_SELF,
        GET_LAST_TIMESTAMP_FOR_FEED
    }
    
    public enum Template {
        SELECT_MESSAGES
    }
    
    public static class MySQLValueFormatter implements BiFunction<DataType, JsonValue, String> {
        @Override
        public String apply(DataType type, JsonValue value) {
            if (type == null) return defaultValueFormatter(type, value);
            switch (type) {
                case UUID:
                    return "UUID_TO_BIN('" + ((JsonString)value).getString() + "')";
                default: return defaultValueFormatter(type,value);
            }
        }
    }    
    
    // I CANNOT EFFING BELIEVE THAT IN 2020 THERE IS NO STANDARD JAVA FUNCTION TO DO THIS
    private static String toHex(byte[] data) {
        final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
        StringBuilder builder = new StringBuilder(2 * data.length);
        for (byte datum : data) builder.append(HEX_CHARS[datum / 16]).append(HEX_CHARS[datum % 16]);
        return builder.toString();
    }

    public static class OracleValueFormatter implements BiFunction<DataType, JsonValue, String> {
        @Override
        public String apply(DataType type, JsonValue value) {
            if (type == null) return defaultValueFormatter(type, value);
            switch (type) {
                case UUID:
                    return "HEXTORAW('" + toHex(Id.of(((JsonString)value).getString()).asBytes())+ "')";
                default: return defaultValueFormatter(type,value);
            }
        }
    }     
    
}
