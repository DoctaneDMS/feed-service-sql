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
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
@ImportResource({"classpath:com/softwareplumbers/feed/service/sql/h2db.xml","classpath:com/softwareplumbers/feed/service/sql/entities.xml"})
public class LocalConfig {
    
    @Autowired
    ApplicationContext context;
    
    @Bean public MessageDatabase database(@Qualifier(value="feed.schema") Schema schema) throws SQLException {
        MessageDatabase database = new MessageDatabase(
            schema,
            context.getBean(OperationStore.class),
            context.getBean(TemplateStore.class)
        );
        database.setCreateOption(CreateOption.RECREATE);
        return database;
    }
    
    @Bean public SQLFeedService testService(MessageDatabase database) throws SQLException {
        return new SQLFeedService(database, 1000000, 2000);
    }
     
    @Bean(name="feed.datasource") public DataSource feedDatasource() {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.h2.Driver");
        dataSourceBuilder.url("jdbc:h2:file:/var/tmp/doctane/test");
        dataSourceBuilder.username("sa");
        dataSourceBuilder.password("");
        return dataSourceBuilder.build();        
    }   
}
