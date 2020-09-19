/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import static com.softwareplumbers.common.sql.AbstractDatabase.defaultValueFormatter;
import com.softwareplumbers.common.sql.Schema;
import java.sql.SQLException;
import java.util.function.BiFunction;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.sql.DataSource;
/**
 *
 * @author jonathan
 */
public class MessageDatabase extends AbstractDatabase<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template, DatabaseInterface> {

    public MessageDatabase(DataSource datasource, Schema<EntityType, DataType> schema) {
        super(datasource, schema);
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
        GET_SELF
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
}
