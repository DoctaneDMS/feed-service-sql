/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import static com.softwareplumbers.common.sql.AbstractDatabase.defaultValueFormatter;
import com.softwareplumbers.common.sql.OperationStore;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.common.sql.TemplateStore;
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

    @Override
    public DatabaseInterface createInterface() throws SQLException {
        return new DatabaseInterface(this);
    }
  
    public enum EntityType {
        MESSAGE,
        FEED
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
        CREATE_MESSAGE,
        GET_MESSAGE
    }
    
    public enum Template {
        SELECT_MESSAGES,
        NAME_EXPR,
        GET_FEED_BY_NAME
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
