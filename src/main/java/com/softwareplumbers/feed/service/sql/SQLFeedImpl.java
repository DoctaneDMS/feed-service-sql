/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.abstractquery.Param;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.feed.FeedExceptions;
import com.softwareplumbers.feed.FeedExceptions.StorageException;
import com.softwareplumbers.feed.FeedService;
import com.softwareplumbers.feed.Filters;
import com.softwareplumbers.feed.Filters.ByRemoteTimestamp;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.MessageIterator;
import com.softwareplumbers.feed.impl.AbstractFeed;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonat
 */
public class SQLFeedImpl extends AbstractFeed {
    
    public static final XLogger LOG = XLoggerFactory.getXLogger(SQLFeedImpl.class);
    
    private class SQLFilters {
        public Predicate<Message> localFilter  = m->true;
        public Query serverFilter = Query.EMPTY;
        public Optional<Filters.ByRemoteTimestamp> byRemoteTimestamp = Optional.empty();
        public Map<String, Object> serverFilterParameters = new TreeMap<>();
        
        public SQLFilters(Predicate<Message>... predicates) {
            int predicateId = 0;
            for (Predicate<Message> predicate : predicates) {
                if (predicate instanceof Filters.FromRemote) {
                    Filters.FromRemote filterDetail = (Filters.FromRemote)predicate;
                    String paramName = "remote" + predicateId;
                    serverFilter = serverFilter.intersect(Query.from(QualifiedName.of("remoteInfo","serverId"), Range.equals(Param.from(paramName))));
                    serverFilterParameters.put(paramName, filterDetail.remote);
                    predicateId++;
                } else if (predicate instanceof Filters.ByRemoteTimestamp) {
                    byRemoteTimestamp = Optional.of((Filters.ByRemoteTimestamp)predicate);
                } else if (predicate == Filters.IS_ACK) {
                    serverFilter = serverFilter.intersect(Query.from("type", Range.equals("ACK")));
                } else {
                    localFilter = localFilter.and(predicate);
                }
            }
        }
    }
    
    public final Id id;
    private final MessageDatabase database;
    
    public SQLFeedImpl(SQLFeedImpl parent, Id id, String name) {
        super(parent, name);
        this.id = id;
        this.database = parent.database;
    } 
    
    public SQLFeedImpl(Id id, MessageDatabase database) {
        super();
        this.id = id;
        this.database = database;
    }
    
    public SQLFeedImpl(MessageDatabase database) {
        this.database = database;
        this.id = Id.ROOT_ID;
    }
    
    public String toString(SQLFeedImpl other) {
        return String.format("SQLFeedImpl[%s,%s]", id, getName());
    }

    @Override
    protected Message[] store(Message... messages) {
        LOG.entry((Object[])messages);
        try (DatabaseInterface ifc = database.getInterface()) {
            Message[] results = ifc.createMessages(id, messages);
            ifc.commit();
            return LOG.exit(results);
        } catch (SQLException ex) {
            throw FeedExceptions.runtime(new StorageException(ex));
        }
    }

    @Override
    public MessageIterator localSearch(FeedService svc, Instant from, boolean fromInclusive, Optional<Instant> to, Optional<Boolean> toInclusive, Predicate<Message>... filters) {
        LOG.entry(svc, from, fromInclusive, to, toInclusive, filters);
        try (DatabaseInterface ifc = database.getInterface()) {
            SQLFilters sqlFilters = new SQLFilters(filters);
            if (sqlFilters.byRemoteTimestamp.isPresent()) {                
                ByRemoteTimestamp remote = sqlFilters.byRemoteTimestamp.get();
                return LOG.exit(MessageIterator.of(ifc.getMessages(id, from, fromInclusive, to, toInclusive, Id.of(remote.serverId.toString()), remote.from, remote.to, sqlFilters.serverFilter, sqlFilters.serverFilterParameters).filter(sqlFilters.localFilter)));                    
            } else {
                return LOG.exit(MessageIterator.of(ifc.getMessages(id, from, fromInclusive, to, toInclusive, sqlFilters.serverFilter, sqlFilters.serverFilterParameters).filter(sqlFilters.localFilter)));    
            }
            
        } catch (SQLException ex) {
            throw FeedExceptions.runtime(new StorageException(ex));
        }
    }

    @Override
    public MessageIterator search(FeedService service, String id, Predicate<Message>... filters) {
        LOG.entry(service, id, filters);
        try (DatabaseInterface ifc = database.getInterface()) {
            return LOG.exit(MessageIterator.of(ifc.getMessages(Id.of(id)).filter(Stream.of(filters).reduce(m->true, Predicate::and))));               
        } catch (SQLException ex) {
            throw FeedExceptions.runtime(new StorageException(ex));
        }
    }    
    
    @Override
    public boolean hasCompleteData(FeedService svc, Instant from) {
        return true;
    }
    
    @Override
    public Optional<Instant> getMyLastTimestamp(FeedService service) {
        LOG.entry();
        try (DatabaseInterface ifc = database.getInterface()) {
            return LOG.exit(ifc.getLastTimestampForFeed(id));               
        } catch (SQLException ex) {
            throw FeedExceptions.runtime(new StorageException(ex));
        }        
    }
}
