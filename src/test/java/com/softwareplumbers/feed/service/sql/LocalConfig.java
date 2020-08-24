package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.AbstractDatabase.CreateOption;
import com.softwareplumbers.common.sql.OperationStore;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.common.sql.Script;
import com.softwareplumbers.common.sql.TemplateStore;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Map;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    
    @ConditionalOnProperty(value = "database.variant", havingValue = "h2")
    @Bean public MessageDatabase databaseH2(
        @Qualifier(value="feed.datasource") DataSource datasource, 
        @Qualifier(value="h2.feed.schema") Schema schema,
        @Qualifier(value="h2.feed.operations") OperationStore<MessageDatabase.Operation> operations,
        @Qualifier(value="h2.feed.templates") TemplateStore<MessageDatabase.Template> templates
    ) throws SQLException {
            
        MessageDatabase database = new MessageDatabase(
            datasource,
            schema
        );
        database.setOperations(operations);
        database.setTemplates(templates);
        database.setCreateOption(CreateOption.RECREATE);
        return database;
    }
    
    @ConditionalOnProperty(value = "database.variant", havingValue = "mysql")
    @Bean public MessageDatabase databaseMySQL(
        @Qualifier(value="feed.datasource") DataSource datasource, 
        @Qualifier(value="mysql.feed.schema") Schema schema,
        @Qualifier(value="mysql.feed.operations") OperationStore<MessageDatabase.Operation> operations,
        @Qualifier(value="mysql.feed.templates") TemplateStore<MessageDatabase.Template> templates
    ) throws SQLException {
            
        MessageDatabase database = new MessageDatabase(
            datasource,
            schema
        );
        database.setOperations(operations);
        database.setTemplates(templates);
        database.setCreateOption(CreateOption.RECREATE);
        return database;
    }    
    
    @ConditionalOnProperty(value = "database.variant", havingValue = "oracle")
    @Bean public MessageDatabase databaseOracle(
        @Qualifier(value="feed.datasource") DataSource datasource, 
        @Qualifier(value="oracle.feed.schema") Schema schema,
        @Qualifier(value="oracle.feed.operations") OperationStore<MessageDatabase.Operation> operations,
        @Qualifier(value="oracle.feed.templates") TemplateStore<MessageDatabase.Template> templates
    ) throws SQLException {
            
        MessageDatabase database = new MessageDatabase(
            datasource,
            schema
        );
        database.setOperations(operations);
        database.setTemplates(templates);
        database.setCreateOption(CreateOption.RECREATE);
        return database;
    }    
    
    @Bean public SQLFeedService testService(MessageDatabase database) throws SQLException {
        return new SQLFeedService(database, 1000000, 2000);
    }
     
    @Bean(name="feed.datasource") public DataSource feedDatasource() {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(env.getProperty("database.driver"));
        dataSourceBuilder.url(env.getProperty("database.url"));
        dataSourceBuilder.username(env.getProperty("database.user"));
        dataSourceBuilder.password(env.getProperty("database.password"));
        return dataSourceBuilder.build();        
    }   
}
