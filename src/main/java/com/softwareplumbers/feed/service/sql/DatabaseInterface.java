/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.abstractquery.Param;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.abstractquery.visitor.Visitors.ParameterizedSQL;
import com.softwareplumbers.common.sql.AbstractInterface;
import com.softwareplumbers.common.sql.FluentStatement;
import com.softwareplumbers.common.sql.Mapper;
import com.softwareplumbers.common.sql.OperationStore;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.common.sql.TemplateStore;
import com.softwareplumbers.feed.Feed;
import com.softwareplumbers.feed.FeedExceptions.InvalidPath;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.impl.FeedImpl;
import com.softwareplumbers.feed.impl.MessageImpl;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Operation;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Template;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Type;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonValue;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;


/**
 *
 * @author jonathan
 */
public class DatabaseInterface extends AbstractInterface<MessageDatabase.Type, MessageDatabase.Operation, MessageDatabase.Template> {

    private static final XLogger LOG = XLoggerFactory.getXLogger(DatabaseInterface.class);
    public static final String NULL_VERSION_VALUE = "__CURRENT";
    private static final Range NULL_VERSION = Range.equals(Json.createValue("NULL_VERSION_VALUE"));  
    private static final Feed ROOT_FEED = new FeedImpl(Id.ROOT_ID.toString(), FeedPath.ROOT);
    
    private static final Mapper<Id> GET_ID = results->Id.of(results.getBytes("ID"));
    
    private static final Mapper<Message> GET_MESSAGE = results -> {
            return new MessageImpl(
                FeedPath.valueOf(results.getString(2)), 
                results.getString(3),
                Mapper.toInstant(results.getTimestamp(4)), 
                Mapper.toJson(results.getCharacterStream(6)), 
                results.getBinaryStream(8), 
                results.getLong(7),
                true
            );
    };
    
    private static final Mapper<Feed> GET_FEED = results -> {
        return new FeedImpl(Id.of(results.getBytes(1)).toString(), FeedPath.valueOf(results.getString(6)));
    };
       
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

    FluentStatement getFeedSQL(FeedPath path) {
        ParameterizedSQL criteria = getParameterizedNameQuery("path", path).toExpression(schema.getFormatter(Type.FEED));
        ParameterizedSQL name =  getParametrizedNameExpression(path);
        return templates.getStatement(Template.GET_FEED_BY_NAME, name, criteria);
    }

    FluentStatement getMessagesFromSQL(FeedPath feed) {
        Query feedQuery = getParameterizedNameQuery("path", feed);
        Query messageQuery = Query.intersect(Query.from("feed", feedQuery), Query.from("timestamp", Range.greaterThan(Param.from("from")).intersect(Range.lessThan(Param.from("to")))));
        return templates.getStatement(Template.SELECT_MESSAGES, messageQuery.toExpression(schema.getFormatter(Type.MESSAGE)));        
    }
    
    public Stream<Message> getMessages(FeedPath feed, Instant from, Instant to) throws SQLException {
        LOG.entry(feed, from, to);
        return LOG.exit(
            getMessagesFromSQL(feed)
                .set(CustomTypes.PATH, "path", feed)
                .set("from", from)
                .set("to", to)
                .execute(schema.datasource, GET_MESSAGE)
        );
    }
    
    public void createMessage(Id feedId, Message message) throws SQLException {
        LOG.entry();
        operations.getStatement(Operation.CREATE_MESSAGE)
            .set(CustomTypes.ID, 1, Id.of(message.getId()))
            .set(2, message.getName().toString())
            .set(3, message.getSender())
            .set(4, message.getTimestamp())
            .set(CustomTypes.ID, 5, feedId)
            .set(6, message.getHeaders())
            .set(7, message.getLength())
            .set(8, ()->message.getData())
            .execute(con);
        LOG.exit();
    }
    
    private static final Object LOCK_FEED_CREATION = new Object();

    public Feed getOrCreateFeed(FeedPath path) throws SQLException, InvalidPath {
        LOG.entry(path);
        if (path.isEmpty()) return LOG.exit(ROOT_FEED);
        synchronized (LOCK_FEED_CREATION) {
            Optional<Feed> existing = getFeed(path, GET_FEED);
            if (existing.isPresent()) return LOG.exit(existing.get());
            return LOG.exit(createFeed(path)); 
        }
    }
    
    public Feed getFeed(FeedPath path) throws SQLException, InvalidPath {
        LOG.entry(path);
        if (path.isEmpty()) return LOG.exit(ROOT_FEED);
        return LOG.exit(getFeed(path, GET_FEED).orElseThrow(()->LOG.throwing(new InvalidPath(path))));
    }
    
    Feed createFeed(Feed parent, String name) throws SQLException {
        LOG.entry(parent, name);
        Id id = Id.generate();
        operations.getStatement(Operation.CREATE_FEED)
            .set(CustomTypes.ID, 1, id)
            .set(CustomTypes.ID, 2, Id.of(parent.getId()))
            .set(3, name)
            .set(4, NULL_VERSION_VALUE)
            .execute(con);
        return LOG.exit(new FeedImpl(id.toString(), parent.getName().add(name)));
    }

    Feed createFeed(FeedPath path) throws SQLException, InvalidPath {
        LOG.entry(path);
        if (path.isEmpty()) return ROOT_FEED;
        if (path.part.type == FeedPath.Element.Type.MESSAGEID) throw LOG.throwing(new InvalidPath(path));
        Feed parent = getOrCreateFeed(path.parent);
        return LOG.exit(createFeed(parent, path.part.getName().get()));
    }
    
    <T> Optional<T> getFeed(FeedPath path, Mapper<T> mapper) throws SQLException {
        LOG.entry(path, mapper);
        try (Stream<T> feeds = getFeedSQL(path)
            .set("basePath", FeedPath.ROOT.toString())
            .set(CustomTypes.PATH, "path", path)
            .execute(con, mapper)
        ) {
            return LOG.exit(feeds.findAny());
        }
    }
    
}
