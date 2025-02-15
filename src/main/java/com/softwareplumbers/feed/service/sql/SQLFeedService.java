/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.FeedExceptions;
import com.softwareplumbers.feed.FeedExceptions.StorageException;
import com.softwareplumbers.feed.impl.AbstractFeed;
import com.softwareplumbers.feed.impl.AbstractFeedService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author jonathan
 */
public class SQLFeedService extends AbstractFeedService {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(SQLFeedService.class);
    
    private final MessageDatabase database;
    private ApplicationContext context;
    
    public SQLFeedService(Id nodeId, ScheduledExecutorService executor, Instant startTime, MessageDatabase database) {
        super(nodeId.asUUID(), executor, startTime, new SQLFeedImpl(database));
        this.database = database;
    }
    
    private static SQLNode getNode(UUID id, MessageDatabase database) {
        LOG.entry(database);
        try (DatabaseInterface ifc = database.getInterface()) {
            SQLNode node = ifc.getNode(Id.of(id));
            ifc.commit(); // Because getting the node will create it if it does not exist.
            return LOG.exit(node);
        } catch (SQLException e) {
            throw LOG.throwing(FeedExceptions.runtime(new StorageException(e)));
        } 
    }
    

    
    private SQLFeedService(MessageDatabase database, SQLNode node) {
        this(node.id, Executors.newScheduledThreadPool(5), node.initTime, database);
    }
    
    public SQLFeedService(UUID id, MessageDatabase database) throws SQLException {
        this(database, getNode(id, database));
    }

    @Override
    public AbstractFeed createFeed(AbstractFeed parent, String name) {
        try (DatabaseInterface ifc = database.getInterface()) {
            SQLFeedImpl result = ifc.getOrCreateChild((SQLFeedImpl)parent, name);
            ifc.commit();
            return result;
        } catch (SQLException e) {
            throw FeedExceptions.runtime(new StorageException(e));
        }
    }

    @Override
    protected String generateMessageId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public void close() throws Exception {
        super.close();
        database.close();
    }
}
