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
import java.math.BigDecimal;
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
    Resolver<Cluster.Host> resolverClusters() {
        return (uri, credentials) -> {
            if (uri.equals(TEST_URI_A)) return Optional.of(context.getBean("testSimpleCluster", Cluster.class).getLocalHost());
            if (uri.equals(TEST_URI_B)) return Optional.of(context.getBean("remoteSimpleCluster", Cluster.class).getLocalHost());
            return Optional.empty();
        };
    }

    public JsonObject dbCredentials() {
        return Json.createObjectBuilder()
            .add("username", env.getProperty("database.user"))
            .add("password", env.getProperty("database.password"))
            .build();
    }
    
    @Bean
    @Scope("singleton")
    Cluster testSimpleCluster(
        @Qualifier("resolverClusters") Resolver<Cluster.Host> resolverClusters,
        DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template>  configFactory,
        @Qualifier("cleanDatabase") MessageDatabase cleanDb // not really a dependency, a hack to ensure we start with a clean database
    ) throws IOException, SQLException {        
        FilesystemCluster cluster = new FilesystemCluster(
            Executors.newFixedThreadPool(4), 
            Paths.get(env.getProperty("installation.root")).resolve("cluster.json"), 
            TEST_URI_A,
            resolverClusters
        );
        URI databaseURI = URI.create(env.getProperty("database.url"));
        JsonObject credentials = dbCredentials();
        cluster.register(new SQLFeedService(TEST_UUID_C, databaseURI, credentials, configFactory));
        return cluster;
    }

    @Bean
    @Scope("singleton")
    Cluster remoteSimpleCluster(
        @Qualifier("resolverClusters") Resolver<Cluster.Host>  resolverClusters,
        DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template>  configFactory,
        @Qualifier("cleanDatabase") MessageDatabase cleanDb // not really a dependency, a hack to ensure we start with a clean database
    ) throws IOException, SQLException {        
        FilesystemCluster cluster = new FilesystemCluster(
            Executors.newFixedThreadPool(4), 
            Paths.get(env.getProperty("installation.root")).resolve("cluster.json"), 
            TEST_URI_B, 
            resolverClusters
        );
        URI databaseURI = URI.create(env.getProperty("database.url"));
        JsonObject credentials = dbCredentials();
        cluster.register(new SQLFeedService(TEST_UUID_C, databaseURI, credentials, configFactory));
        return cluster;
    }
    
    @Bean
    @Scope("singleton")
    FeedService testSimpleClusterNodeA(@Qualifier("testSimpleCluster") Cluster cluster) throws URISyntaxException, IOException {
        FeedService nodeA = new DummyFeedService(UUID.randomUUID(), 100000, 2000);
        cluster.getLocalHost().register(nodeA);
        return nodeA;
    }

    @Bean
    @Scope("singleton")
    FeedService testSimpleClusterNodeB(@Qualifier("remoteSimpleCluster") Cluster cluster) throws URISyntaxException, IOException {
        FeedService nodeB = new DummyFeedService(UUID.randomUUID(), 100000, 2000);
        cluster.getLocalHost().register(nodeB);
        return nodeB;
    }
    
    @Bean
    @Scope("singleton")
    FeedService testSimpleClusterNodeC(
        DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template>  configFactory
    ) throws URISyntaxException, IOException, SQLException {
        URI databaseURI = URI.create(env.getProperty("database.url"));
        JsonObject credentials = dbCredentials();
        return new SQLFeedService(TEST_UUID_C, databaseURI, credentials, configFactory);
    }    
}
