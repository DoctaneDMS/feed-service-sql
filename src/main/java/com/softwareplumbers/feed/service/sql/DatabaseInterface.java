/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.abstractquery.Param;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.sql.AbstractInterface;
import com.softwareplumbers.common.sql.FluentStatement;
import com.softwareplumbers.common.sql.Mapper;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.FeedExceptions.InvalidPathSyntax;
import static com.softwareplumbers.feed.FeedExceptions.runtime;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.Message.RemoteInfo;
import com.softwareplumbers.feed.MessageType;
import com.softwareplumbers.feed.impl.MessageImpl;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Operation;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Template;
import com.softwareplumbers.feed.service.sql.MessageDatabase.EntityType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.json.Json;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;


/**
 *
 * @author Jonathan Essex
 */
public class DatabaseInterface extends AbstractInterface<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> {

    private static final XLogger LOG = XLoggerFactory.getXLogger(DatabaseInterface.class);
    public static final String NULL_VERSION_VALUE = "__CURRENT";
    private static final Range NULL_VERSION = Range.equals(Json.createValue(NULL_VERSION_VALUE));  
    //private static final SQLFeedImpl ROOT_FEED = new SQLFeedImpl();
    
    private static final Mapper<Id> GET_ID = results->Id.of(results.getBytes("ID"));
    private static final Mapper<Instant> GET_TIMESTAMP = results->results.getTimestamp(1).toInstant();
    private static final Mapper<Optional<Instant>> GET_OPTIONAL_TIMESTAMP = results->Optional.ofNullable(results.getTimestamp(1)).map(Timestamp::toInstant);
    private static final Mapper<Id> GET_SELF = results->Id.of(results.getBytes(1));
    
    private static final Optional<RemoteInfo> getRemoteInfo(ResultSet results) throws SQLException {
        byte[] serverId = results.getBytes(10);
        if (serverId == null)
            return Optional.empty();
        else
            return Optional.of(new RemoteInfo(UUID.nameUUIDFromBytes(serverId), Mapper.toInstant(results.getTimestamp(11))));
    }
    
    private static final Mapper<Message> GET_MESSAGE = results -> {
        
        try {
            return new MessageImpl(
                MessageType.valueOf(results.getString(9)),
                FeedPath.valueOf(results.getString(2)), 
                results.getString(3),
                Mapper.toInstant(results.getTimestamp(4)), // timestamp
                Optional.ofNullable(results.getBytes(12)).map(Id::of).map(Id::asUUID), // serverId
                getRemoteInfo(results),
                Mapper.toJson(results.getCharacterStream(6)), 
                results.getBinaryStream(8), 
                results.getLong(7),
                false 
            );
        } catch (InvalidPathSyntax pse) {
            throw runtime(pse);
        }
                
    };
    
    private static final Mapper<SQLNode> GET_NODE = results -> {
        return new SQLNode(
            Id.of(results.getBytes(1)),
            results.getTimestamp(2).toInstant() 
        );
    };
    
    private static final Mapper<SQLFeedImpl> getChildOf(SQLFeedImpl parent) {
        return results -> {
            return new SQLFeedImpl(parent, Id.of(results.getBytes(1)), results.getString(3));
        };
    }
       
    public DatabaseInterface(MessageDatabase database) throws SQLException {
        super(database);
    }
     
     Query getMessagesRangeQuery(Id feedId, boolean fromInclusive, Optional<Boolean> toInclusive, Query filter) {
        Query feedQuery = Query.from("feedId", Range.equals(Param.from("feedId")));
        Query fromQuery = Query.from("timestamp", fromInclusive ? Range.greaterThanOrEqual(Param.from("from")) : Range.greaterThan(Param.from("from")));
        Query toQuery = toInclusive.isPresent() ? Query.from("timestamp", toInclusive.get() ? Range.lessThanOrEqual(Param.from("to")) : Range.lessThan(Param.from("to"))) : Query.UNBOUNDED;
        return feedQuery.intersect(fromQuery).intersect(toQuery).intersect(filter);
    }

    Query getRemoteMessagesRangeQuery(Id feedId, boolean fromInclusive, Optional<Boolean> toInclusive, Query filter) {
        Query baseQuery = getMessagesRangeQuery(feedId, false, toInclusive, filter);
        Query serverIdQuery = Query.from("serverId", Range.equals(Param.from("serverId")));
        Query fromQuery = Query.from("timestamp", fromInclusive ? Range.greaterThanOrEqual(Param.from("fromRemote")) : Range.greaterThan(Param.from("fromRemote")));
        Query toQuery = toInclusive.isPresent() ? Query.from("timestamp", toInclusive.get() ? Range.lessThanOrEqual(Param.from("toRemote")) : Range.lessThan(Param.from("toRemote"))) : Query.UNBOUNDED;
        return baseQuery.intersect(Query.from("remoteInfo", fromQuery.intersect(toQuery))).intersect(serverIdQuery);
    }    
    
    FluentStatement getMessagesAtSQL() {
        return templates.getStatement(
            Template.SELECT_MESSAGES, 
            Query.UNBOUNDED
                .intersect(Query.from("timestamp", Range.equals(Param.from("timestamp"))))
                .intersect(Query.from("feedId", Range.equals(Param.from("feedId"))))
            .toExpression(schema.getFormatter(EntityType.MESSAGE)));
    }
    
    FluentStatement getMessagesFromSQL(Id feedId, boolean fromInclusive, Optional<Boolean> toInclusive, Query filter) {
        return templates.getStatement(Template.SELECT_MESSAGES, getMessagesRangeQuery(feedId, fromInclusive, toInclusive, filter).toExpression(schema.getFormatter(EntityType.MESSAGE)));        
    }
    
    FluentStatement getRemoteMessagesFromSQL(Id feedId, boolean fromInclusive, Optional<Boolean> toInclusive, Query filter) {
        return templates.getStatement(Template.SELECT_MESSAGES, getRemoteMessagesRangeQuery(feedId, fromInclusive, toInclusive, filter).toExpression(schema.getFormatter(EntityType.REMOTE_MESSAGE)));        
    }

    public Stream<Message> getMessages(Id feedId, Instant from, boolean fromInclusive, Optional<Instant> to, Optional<Boolean> toInclusive, Query filter, Map<String,Object> filterParameters) throws SQLException {
        LOG.entry(feedId, from, fromInclusive, to, toInclusive);
        FluentStatement statement = getMessagesFromSQL(feedId, fromInclusive, toInclusive, filter)
            .set(CustomTypes.ID, "feedId", feedId)
            .set("from", from);

        if (to.isPresent()) statement = statement.set("to", to.get());
        
        return LOG.exit(statement.execute(database.getDataSource(), GET_MESSAGE));
    }

    public Stream<Message> getMessages(Id feedId, Instant from, boolean fromInclusive, Optional<Instant> to, Optional<Boolean> toInclusive, Id remote, Instant fromRemote, Optional<Instant> toRemote, Query filter, Map<String,Object> filterParameters) throws SQLException {
        LOG.entry(feedId, from, fromInclusive, to, toInclusive);
        FluentStatement statement = getRemoteMessagesFromSQL(feedId, fromInclusive, toInclusive, filter)
                .set(CustomTypes.ID, "feedId", feedId)
                .set("from", fromRemote)
                .set(CustomTypes.ID, "serverId", remote)
                .set("fromRemote", from);
        
        if (to.isPresent()) statement = statement.set("toRemote", to.get());
        if (toRemote.isPresent()) statement = statement.set("to", toRemote.get());
            
        return LOG.exit(statement.execute(database.getDataSource(), GET_MESSAGE));
    }

    public Stream<Message> getMessages(Id messageId) throws SQLException {
        LOG.entry(messageId);
        return LOG.exit(
            operations.getStatement(Operation.GET_MESSAGES_BY_ID)
                .set(CustomTypes.ID, 1, messageId)
                .execute(database.getDataSource(), GET_MESSAGE)
        );
    }
    
    private final Comparator<Message> TYPE_COMPARATOR = Comparator.comparing(Message::getType);
    private final Comparator<Message> ID_COMPARATOR = Comparator.comparing(Message::getId);
    private final Comparator<Message> UNIQUE_MESSAGES = ID_COMPARATOR.thenComparing(TYPE_COMPARATOR);
    
    public Message[] createMessages(Id feedId, Message... messages) throws SQLException {
        LOG.entry(feedId, messages);
        
        operations.getStatement(Operation.GENERATE_TIMESTAMP).execute(con);
        
        operations.getStatementForBatch(Operation.CREATE_MESSAGE)
            .execute(con, Stream.of(messages), (statement, message)->
                statement.set(CustomTypes.ID, 1, Id.of(message.getId()))
                    .set(2, message.getName().toString())
                    .set(3, message.getSender())
                    .set(CustomTypes.ID, 4, feedId)
                    .set(5, message.getHeaders())
                    .set(6, message.getLength())
                    .set(7, ()->message.getData())
                    .set(8, message.getType().toString())
                    .set(CustomTypes.ID, 9, message.getRemoteInfo().map(ri->Id.of(ri.serverId)).orElse(null))
                    .set(10, message.getRemoteInfo().map(ri->ri.timestamp).orElse(null))
        );
        
        Map<Message,Integer> originalOrder = IntStream.range(0, messages.length)
            .mapToObj(Integer::new)
            .collect(Collectors.toMap(i->messages[i], i->i, (a,b)->{ throw new RuntimeException("Duplicate values"); }, ()->new TreeMap<>(UNIQUE_MESSAGES)));
        
        return LOG.exit(
            operations.getStatement(Operation.GET_NEW_MESSAGES)
                .set(CustomTypes.ID, 1, feedId)
                .execute(con, GET_MESSAGE)
                .sorted(Comparator.comparing(originalOrder::get))
                .toArray(Message[]::new)
        );
    }
    

    public SQLFeedImpl getOrCreateChild(SQLFeedImpl parent, String name) throws SQLException {
        LOG.entry(parent, name);
        try {
            return LOG.exit(createFeed(parent, name)); 
        } catch (SQLIntegrityConstraintViolationException sql) {
            return LOG.exit(getFeed(parent, name)).get();
        }
    }
    
    SQLFeedImpl createFeed(SQLFeedImpl parent, String name) throws SQLException {
        LOG.entry(parent, name);
        Id id = Id.generate();
        operations.getStatement(Operation.CREATE_FEED)
            .set(CustomTypes.ID, 1, id)
            .set(CustomTypes.ID, 2, parent.id)
            .set(3, name)
            .set(4, NULL_VERSION_VALUE)
            .execute(con);
        return LOG.exit(new SQLFeedImpl(parent, id, name));
    }
 
    Optional<SQLFeedImpl> getRootFeed() throws SQLException {
        LOG.entry();
        try (Stream<SQLFeedImpl> feeds = operations.getStatement(Operation.GET_FEED_BY_ID)
            .set(CustomTypes.ID, 1, Id.ROOT_ID)
            .execute(con, row->new SQLFeedImpl(Id.ROOT_ID, (MessageDatabase)database)) // This actually doesn't need the database call, but leaving it in because ultimately it will.
        ) {
            return LOG.exit(feeds.findAny());
        }
    }
    
    Optional<SQLFeedImpl> getFeed(SQLFeedImpl parent, String name) throws SQLException {
        LOG.entry(parent, name);
        try (Stream<SQLFeedImpl> feeds = operations.getStatement(Operation.GET_FEED_BY_ID_AND_NAME)
            .set(CustomTypes.ID, 1, parent.id)
            .set(2, name)
            .execute(con, getChildOf(parent))
        ) {
            return LOG.exit(feeds.findAny());
        }
    }
    
    void createSelf(Id self) throws SQLException {
        LOG.entry(self);
        int count = operations.getStatement(Operation.CREATE_SELF)
            .set(CustomTypes.ID, 1, self)
            .execute(con);
        if (count < 1) throw new RuntimeException("Self entry not created");
        LOG.exit();
    }
    
    void createNode(Id self) throws SQLException {
        LOG.entry(self);
        operations.getStatement(Operation.GENERATE_TIMESTAMP).execute(con);

        operations.getStatement(Operation.CREATE_NODE)
            .set(CustomTypes.ID, 1, self)
            .execute(con);
        LOG.exit();
    }

    SQLNode getNode(Id id) throws SQLException {
        LOG.entry();
        Optional<Id> self = 
            operations.getStatement(Operation.GET_SELF)
                .execute(con, GET_SELF)
                .findAny();
        LOG.debug("self {}", self);
        if (self.isPresent()) {
            if (!self.get().equals(id)) throw new RuntimeException("SQL node has foreign Id"); 
        } else {
            self = Optional.of(id); createSelf(id); 
        }
        Optional<SQLNode> node = operations.getStatement(Operation.GET_NODE)
            .set(CustomTypes.ID, 1, self.get())
            .execute(con, GET_NODE)
            .findAny();
        LOG.debug("node {}", node);
        if (!node.isPresent()) {
            createNode(id);
            node = operations.getStatement(Operation.GET_NODE)
                .set(CustomTypes.ID, 1, self.get())
                .execute(con, GET_NODE)
                .findAny();            
        }
        return node.get();
    }
    
    public Optional<Instant> getLastTimestampForFeed(Id feedId) throws SQLException {
        LOG.entry(feedId);
        return LOG.exit(
            operations.getStatement(Operation.GET_LAST_TIMESTAMP_FOR_FEED)
                .set(CustomTypes.ID, 1, feedId)
                .execute(con, GET_OPTIONAL_TIMESTAMP)
                .findAny()
                .get()
        );        
    }
    
}
