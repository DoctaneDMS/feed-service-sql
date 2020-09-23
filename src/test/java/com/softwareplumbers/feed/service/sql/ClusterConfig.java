package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase;
import com.softwareplumbers.common.sql.DatabaseConfig;
import com.softwareplumbers.common.sql.DatabaseConfigFactory;
import com.softwareplumbers.feed.Cluster;
import com.softwareplumbers.feed.impl.FilesystemCluster;
import com.softwareplumbers.feed.FeedService;
import com.softwareplumbers.feed.impl.Resolver;
import com.softwareplumbers.feed.test.DummyFeedService;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.sql.DataSource;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
@ImportResource({"classpath:com/softwareplumbers/feed/service/sql/mysqldb.xml","classpath:com/softwareplumbers/feed/service/sql/oradb.xml","classpath:com/softwareplumbers/feed/service/sql/h2db.xml","classpath:com/softwareplumbers/feed/service/sql/entities.xml"})
public class ClusterConfig {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(ClusterConfig.class);
    
    @Autowired
    ApplicationContext context;
    
    @Autowired
    Environment env;
    
        // This is listed as a dependency just to make sure we start with a clean DB for test purposes
    @Bean public MessageDatabase cleanDatabase(DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> config) throws SQLException {
        DataSource ds = DataSourceBuilder.create()
            .url(env.getProperty("database.url"))
            .username(env.getProperty("database.user"))
            .password(env.getProperty("database.password"))
            .build();
        MessageDatabase database = new MessageDatabase(ds, config, AbstractDatabase.CreateOption.RECREATE);
        database.initialize();
        return database;
    }
    
    @Bean
    public DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> configFactory() {
        return variant-> {
            switch(variant) {
                case H2: return context.getBean("h2.feed.config", DatabaseConfig.class);
                case MYSQL: return context.getBean("mysql.feed.config", DatabaseConfig.class);
                case ORACLE: return context.getBean("oracle.feed.config", DatabaseConfig.class);
                default: throw new RuntimeException("Unhandled variant " + variant);
            }
        };                  
    }    

    public static final URI TEST_URI_A = URI.create("http://testA.net");
    public static final URI TEST_URI_B = URI.create("http://testB.net");
    public static final UUID TEST_UUID_C = UUID.fromString("da7aa4a9-cb2d-4525-aa87-184f1ae1f642");
    
    @Bean
    @Scope("singleton") 
    Resolver<FeedService> resolverFeeds(@Qualifier("testSimpleClusterNodeA") FeedService a, @Qualifier("testSimpleClusterNodeB") FeedService b, DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> config) {
        final URI TEST_URI_C = URI.create(env.getProperty("database.url"));
        return (uri, credentials) -> {
            LOG.entry(uri, credentials);
            if (uri.equals(TEST_URI_A)) return Optional.of(a);
            if (uri.equals(TEST_URI_B)) return Optional.of(b);
            if (uri.equals(TEST_URI_C)) {
                try {
                    return Optional.of(new SQLFeedService(TEST_UUID_C, uri, credentials, config));
                } catch (SQLException exp) {
                    LOG.error("Exception while creating SQLFeedService", exp);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        };
    }

    @Bean
    @Scope("singleton") 
    Resolver<Cluster> resolverClusters(@Lazy @Qualifier("testSimpleCluster") Cluster a, @Lazy @Qualifier("remoteSimpleCluster") Cluster b) {
        return (uri, credentials) -> {
            if (uri.equals(TEST_URI_A)) return Optional.of(a);
            if (uri.equals(TEST_URI_B)) return Optional.of(b);
            return Optional.empty();
        };
    }

    @Bean
    @Scope("singleton")
    Cluster testSimpleCluster(
        @Qualifier("resolverFeeds") Resolver<FeedService> resolverFeeds, 
        @Qualifier("resolverClusters") Resolver<Cluster> resolverClusters,
        @Qualifier("testSimpleClusterNodeA") FeedService nodeA,
        @Qualifier("cleanDatabase") MessageDatabase cleanDb // not really a dependency, a hack to ensure we start with a clean database
    ) throws IOException {        
        FilesystemCluster cluster = new FilesystemCluster(
            Executors.newFixedThreadPool(4), 
            Paths.get(env.getProperty("installation.root")).resolve("cluster"), 
            resolverFeeds, 
            resolverClusters
        );
        URI databaseURI = URI.create(env.getProperty("database.url"));
        cluster.setCredential(databaseURI, env.getProperty("database.user"), env.getProperty("database.password"));
        cluster.register(nodeA, TEST_URI_A);
        //cluster.register(TEST_UUID_C, databaseURI);
        return cluster;
    }

    @Bean
    @Scope("singleton")
    Cluster remoteSimpleCluster(
        @Qualifier("resolverFeeds") Resolver<FeedService> resolverFeeds, 
        @Qualifier("resolverClusters") Resolver<Cluster>  resolverClusters,
        @Qualifier("testSimpleClusterNodeB") FeedService nodeB,
        @Qualifier("cleanDatabase") MessageDatabase cleanDb // not really a dependency, a hack to ensure we start with a clean database
    ) throws IOException {        
        FilesystemCluster cluster = new FilesystemCluster(
            Executors.newFixedThreadPool(4), 
            Paths.get(env.getProperty("installation.root")).resolve("cluster"), 
            resolverFeeds, 
            resolverClusters
        );
        URI databaseURI = URI.create(env.getProperty("database.url"));
        cluster.setCredential(databaseURI, env.getProperty("database.user"), env.getProperty("database.password"));
        cluster.register(nodeB, TEST_URI_B);
        //cluster.register(TEST_UUID_C, databaseURI);
        return cluster;
    }
    
    @Bean
    @Scope("singleton")
    FeedService testSimpleClusterNodeA() throws URISyntaxException, IOException {
        FeedService nodeA = new DummyFeedService(100000, 2000);
        return nodeA;
    }

    @Bean
    @Scope("singleton")
    FeedService testSimpleClusterNodeB() throws URISyntaxException, IOException {
        FeedService nodeB = new DummyFeedService(100000, 2000);
        return nodeB;
    }
}
