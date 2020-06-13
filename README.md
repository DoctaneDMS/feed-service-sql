# Doctane Feed SQL Service Module

This package contains a Doctane feed service build on a generic SQL store for
messages. 

This service is the _default service_ for the Doctane REST server for feeds (rest-server-feeds).
When build as a standalone spring boot application with the default configuration,
rest-server-feed will use this service to store messages. See the rest-server-feed
package [here](https://projects.softwareplumbers.com/document-management/rest-server-feeds)
for information about installing the Doctane REST server for feeds.

## Configuration

### services.xml

The H2 SQLFeedService is the reference implementation of a Doctane FeedService. Sample
configuration is included below. Firstly, we must import the database scripts needed to create and
the database schema and the SQL statements necessary to implement common Doctane operations on the
database.

```xml    
    <import resource="classpath:com/softwareplumbers/feed/service/sql/h2db.xml" />
    <import resource="classpath:com/softwareplumbers/feed/service/sql/entities.xml" />
```  

The standard h2db.xml file should be reasonably compatible with most SQL servers and
can be modified in order to support any SQL dialect. As well as the templated operations
included in the xml configuration above, the SQL service module also generates certain
statements and clauses programatically. This is done in the DocumentDatabase class, which
is configured below:

```xml   
    <bean id="database" class="com.softwareplumbers.feed.service.sql.MessageDatabase">
        <property name="createOption" value="RECREATE"/>
        <property name="operations" ref="feed.operations"/>
        <property name="templates" ref="feed.templates"/>
    </bean>
```

The createOption property above is optional and determines what SQL scripts will be run on 
service startup. Possible values are CREATE, UPDATE, and RECREATE. CREATE will attempt to
create the database schema. UPDATE will attempt to update the schema (although this is not
always possible). RECREATE will drop any existing database objects and recreate the schema
from scratch. If the option is not included, no attempt will be made to modify the schema.

Next we have some standard boilerplate for configuring the database connection:

```xml
	<bean id="datasource" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
		<property name="driverClassName" value="org.h2.Driver" />
		<property name="url" value="jdbc:h2:file:/var/tmp/doctane/db" />
		<property name="username" value="sa" />
		<property name="password" value="" />
	</bean> 
```    

Then finally we can create the SQLFeedService bean itself:

```xml 
    <bean id="tmp" class="com.softwareplumbers.feed.service.sql.SQLFeedService" scope="singleton">
        <constructor-arg index="0" ref="database"/>
        <constructor-arg index="1" value="1000000"/>
        <constructor-arg index="2" value="10000"/>
    </bean>
```

The first parameter references the message database bean configured above. The second parameter
defines the combined maximum size in bytes of the feed server's message buffers. The third parameter
defines the initial size of an individual message buffer. This should be greater than the maximum
expected size of any message.
 
### LocalConfig.java

The generic SQL storage service can also be configured using java annotations. However, since
the XML format allows SQL statements to be formatted more naturally and readably, we recommend
that the h2db.xml and entities.xml files should remain in XML format. As shown below, the
XML configuration for these beans can be imported into the java configuration quite simply.

```java
@ImportResource({"classpath:com/softwareplumbers/feed/service/sql/h2db.xml","classpath:com/softwareplumbers/feed/service/sql/entities.xml"})
public class LocalConfig {
    
    @Bean public MessageDatabase database(
        OperationStore<MessageDatabase.Operation> operations,
        TemplateStore<MessageDatabase.Template> templates,
        Schema schema
    ) {
        MessageDatabase database = new MessageDatabase(schema);
        database.setOperations(operations);
        database.setTemplates(templates);
        return database;
    }
    
    @Bean public SQLFeedService service(MessageDatabase database) throws SQLException {
        return new SQLFeedService(database, filestore);
    }
     
    @Bean public DataSource datasource() {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.h2.Driver");
        dataSourceBuilder.url("jdbc:h2:file:/var/tmp/doctane/test");
        dataSourceBuilder.username("sa");
        dataSourceBuilder.password("");
        return dataSourceBuilder.build();        
    }  
}
```

## JMX Management Beans

Also provided is a simple JMX management bean. Currently this bean just provides
a way to dump the state of the message buffer pool to the system log. The been is registered
with spring via standard spring configuration; however the exact configuration may
vary depending on choice of application server. In general, something similar to the below
will be required:

```xml
    <bean id="tmpManager" class="com.softwareplumbers.feed.service.sql.SQLFeedServiceMBean">
        <constructor-arg index="0" ref="tmp" />
    </bean>
    
    <bean id="exporter" class="org.springframework.jmx.export.MBeanExporter">
        <property name="beans">
            <map>
                <entry key="bean:name=feed.service.tmp" value-ref="tmpManager"/>
            </map>
        </property>
        <property name="server" ref="mbeanServer"/>
    </bean>
```

