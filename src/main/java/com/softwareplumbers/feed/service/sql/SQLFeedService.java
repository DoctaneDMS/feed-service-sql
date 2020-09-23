/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import com.softwareplumbers.common.sql.AbstractDatabase.CreateOption;
import com.softwareplumbers.common.sql.DatabaseConfigFactory;
import com.softwareplumbers.feed.FeedExceptions;
import com.softwareplumbers.feed.FeedExceptions.StorageException;
import com.softwareplumbers.feed.impl.AbstractFeed;
import com.softwareplumbers.feed.impl.AbstractFeedService;
import com.softwareplumbers.feed.service.sql.MessageDatabase.DataType;
import com.softwareplumbers.feed.service.sql.MessageDatabase.EntityType;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Operation;
import com.softwareplumbers.feed.service.sql.MessageDatabase.Template;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.json.JsonObject;
import javax.sql.DataSource;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

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
    
    private static MessageDatabase getDatabase(URI jdbcURI, JsonObject credentials, DatabaseConfigFactory<EntityType,DataType,Operation,Template> config) throws SQLException {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcURI.toString());
        ds.setUsername(credentials.getString("username"));
        ds.setPassword(credentials.getString("password"));    
        return new MessageDatabase(ds, config, CreateOption.NONE);
    }
    
    private SQLFeedService(MessageDatabase database, SQLNode node) {
        this(node.id, Executors.newScheduledThreadPool(5), node.initTime, database);
    }
    
    public SQLFeedService(UUID id, MessageDatabase database) throws SQLException {
        this(database, getNode(id, database));
    }
    
    public SQLFeedService(UUID id, URI jdbcURI, JsonObject credentials, DatabaseConfigFactory<EntityType,DataType,Operation,Template> config) throws SQLException {
        this(id, getDatabase(jdbcURI, credentials, config));
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
