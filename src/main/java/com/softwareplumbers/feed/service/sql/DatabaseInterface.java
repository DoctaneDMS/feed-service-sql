/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.abstractquery.Param;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.abstractquery.visitor.Visitors;
import com.softwareplumbers.common.abstractquery.visitor.Visitors.ParameterizedSQL;
import com.softwareplumbers.common.sql.AbstractInterface;
import com.softwareplumbers.common.sql.OperationStore;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.common.sql.TemplateStore;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Template;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Type;
import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonValue;


/**
 *
 * @author jonathan
 */
public class DatabaseInterface extends AbstractInterface<MessageDatabase.Type, MessageDatabase.Operation, MessageDatabase.Template> {

    private static final Range NULL_VERSION = Range.equals(Json.createValue(""));
    
    public DatabaseInterface(Schema<MessageDatabase.Type> schema, OperationStore<MessageDatabase.Operation> operations, TemplateStore<MessageDatabase.Template> templates) throws SQLException {
        super(schema, operations, templates);
    }
    
    Query getNameQuery(FeedPath name, boolean hideDeleted) {        
        if (name.isEmpty()) return Query.UNBOUNDED;
        
        Query result;
        
        if (name.parent.isEmpty()) {
            if (name.part.type != FeedPath.Element.Type.FEEDID) {
                result = Query.from("parentId", Range.equals(Json.createValue(Id.ROOT_ID.toString())));              
            } else {
                result = Query.UNBOUNDED;
            }
        } else {        
            // First get the query for the parent part of the name
            if (name.parent.part.type == FeedPath.Element.Type.FEEDID) {
                result = Query.from("parentId", Range.like(name.parent.part.getId().get()));
            } else {
                result = Query.from("parent", getNameQuery(name.parent, hideDeleted));
            }
        }
        
        // Filter out anything that has been deleted
        if (hideDeleted) result = result.intersect(Query.from("deleted", Range.equals(JsonValue.FALSE)));
        
        // Now add the query for this part of the name
        switch (name.part.type) {
            case FEED:
                result = result.intersect(Query.from("name", Range.like(name.part.getName().get())));
                result = result.intersect(Query.from("version", name.part.getVersion().map(version->Range.equals(Json.createValue(version))).orElse(NULL_VERSION)));                
                break;
            case FEEDID:
                result = result.intersect(Query.from("id", Range.like(name.part.getId().get())));
                break;
            default:
                throw new RuntimeException("Unsupported element type in path " + name);
        }
        return result;
    } 
    
    Query getParameterizedNameQuery(String paramName, FeedPath name) {
        
        if (name.isEmpty()) return Query.from("id", Range.equals(Param.from(paramName)));
        
        Query result = Query.UNBOUNDED;
                                
        // Now add the query for this part of the name
        switch (name.part.type) {
            case FEED:
                result = result.intersect(Query.from("name", Range.equals(Param.from(paramName))));
                result = result.intersect(Query.from("version", Range.equals(Param.from(paramName+".version"))));                
                break;
            case 
                FEEDID:
                result = result.intersect(Query.from("id", Range.equals(Param.from(paramName))));
                break;
            default:
                throw new RuntimeException("Unsupported element type in path " + name);
        }        

        if (name.parent.isEmpty()) {
            if (name.part.type != FeedPath.Element.Type.FEEDID) {
                result = Query
                    .from("parentId", Range.equals(Param.from("parent." + paramName)))
                    .intersect(result);              
            } 
        } else {        
            // First get the query for the parent part of the name
            if (name.parent.part.type == FeedPath.Element.Type.FEEDID) {
                // this shortcut basically just avoids joining to the parent node if the criteria
                // is just on the node id
                result = Query
                    .from("parentId", Range.equals(Param.from("parent." + paramName)))
                    .intersect(result);
            } else {
                result = Query
                    .from("parent", getParameterizedNameQuery("parent." + paramName, name.parent))
                    .intersect(result);
            }
        }
                
        return result;        
    }
    
    
    String getNameExpression(FeedPath basePath, FeedPath path) {
        StringBuilder builder = new StringBuilder();
        int depth = path.afterFeedId().size();
        builder.append("'").append(basePath.join("/")).append("'");      
        for (int i = depth - 1; i >= 0 ; i--)
            builder.append(templates.getSQL(Template.NAME_EXPR, Integer.toString(i)));
        return builder.toString();
    }
    
    ParameterizedSQL getParametrizedNameExpression(FeedPath path) {
        StringBuilder builder = new StringBuilder();
        int depth = path.afterFeedId().size();
        builder.append("?");      
        for (int i = depth - 1; i >= 0 ; i--)
            builder.append(templates.getSQL(Template.NAME_EXPR, Integer.toString(i)));
        return new ParameterizedSQL(builder.toString(), "basePath");
    }    

    ParameterizedSQL getNodeSQL(FeedPath path) {
        ParameterizedSQL criteria = getParameterizedNameQuery("path", path).toExpression(schema.getFormatter(Type.FEED));
        ParameterizedSQL name =  getParametrizedNameExpression(path);
        return templates.getParameterizedSQL(Template.GET_FEED, name, criteria);
    }    
    
    public Stream<Message> getMessagesFrom(FeedPath room, Instant from) {
        return null;
    }
    
}
