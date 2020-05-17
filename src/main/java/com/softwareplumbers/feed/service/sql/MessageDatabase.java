/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import com.softwareplumbers.common.sql.OperationStore;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.common.sql.TemplateStore;
import java.sql.SQLException;

/**
 *
 * @author jonathan
 */
public class MessageDatabase extends AbstractDatabase<MessageDatabase.Type, MessageDatabase.Operation, MessageDatabase.Template, DatabaseInterface> {

    public MessageDatabase(Schema<Type> schema, OperationStore<Operation> os, TemplateStore<Template> ts) {
        super(schema, os, ts);
    }

    @Override
    public DatabaseInterface createInterface(Schema<Type> schema, OperationStore<Operation> os, TemplateStore<Template> ts) throws SQLException {
        return new DatabaseInterface(schema, os, ts);
    }
  
    public enum Type {
        MESSAGE,
        FEED
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
}
