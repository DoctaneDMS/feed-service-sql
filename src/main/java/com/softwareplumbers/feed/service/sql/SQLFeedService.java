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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
public class SQLFeedService extends AbstractFeedService {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(SQLFeedService.class);
    
    private final MessageDatabase database;
    
    public SQLFeedService(Id nodeId, ExecutorService executor, Instant startTime, MessageDatabase database) {
        super(nodeId.asUUID(), executor, startTime, new SQLFeedImpl(database));
        this.database = database;
    }
    
    
    private static SQLNode getNode(MessageDatabase database) {
        LOG.entry(database);
        try (DatabaseInterface ifc = database.getInterface()) {
            SQLNode node = ifc.getNode();
            ifc.commit(); // Because getting the node will create it if it does not exist.
            return LOG.exit(node);
        } catch (SQLException e) {
            throw LOG.throwing(FeedExceptions.runtime(new StorageException(e)));
        } 
    }
    
    private SQLFeedService(MessageDatabase database, SQLNode node) {
        this(node.id, Executors.newFixedThreadPool(5), node.initTime, database);
    }
    
    public SQLFeedService(MessageDatabase database) throws SQLException {
        this(database, getNode(database));
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
 
}
