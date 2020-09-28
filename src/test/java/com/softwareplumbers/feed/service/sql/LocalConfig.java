package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase.CreateOption;
import com.softwareplumbers.common.sql.DatabaseConfig;
import com.softwareplumbers.common.sql.DatabaseConfigFactory;
import java.net.URI;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
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
public class LocalConfig {
    
    @Autowired
    ApplicationContext context;
    
    @Autowired
    Environment env;

    public static final UUID TEST_UUID_C = UUID.randomUUID();
    
    
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
    
    public Properties dbCredentials() {
        Properties credentials = new Properties();
        credentials.put("username", env.getProperty("database.user"));
        credentials.put("password", env.getProperty("database.password"));
        return credentials;
    }

    // This is listed as a dependency just to make sure we start with a clean DB for test purposes
    @Bean public MessageDatabase cleanDatabase(DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> config) throws SQLException {
        DataSource ds = DataSourceBuilder.create()
            .url(env.getProperty("database.url"))
            .username(env.getProperty("database.user"))
            .password(env.getProperty("database.password"))
            .build();
        MessageDatabase database = new MessageDatabase(ds, config, CreateOption.RECREATE);
        database.initialize();
        return database;
    }
        
    @Bean public SQLFeedService testService(DatabaseConfigFactory<MessageDatabase.EntityType, MessageDatabase.DataType, MessageDatabase.Operation, MessageDatabase.Template> config, @Qualifier("cleanDatabase") MessageDatabase cleaner) throws SQLException {
        return new SQLFeedService(TEST_UUID_C, URI.create(env.getProperty("database.url")), config, dbCredentials());
    }     
}
