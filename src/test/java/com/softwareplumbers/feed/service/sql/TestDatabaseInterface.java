/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.pipedstream.OutputStreamConsumer;
import com.softwareplumbers.common.sql.Schema;
import com.softwareplumbers.feed.Feed;
import com.softwareplumbers.feed.FeedExceptions.InvalidPath;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.impl.MessageImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Stream;
import javax.json.JsonObject;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author jonathan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { LocalConfig.class })
public class TestDatabaseInterface {
    
    @Autowired
    MessageDatabase database;
    
    @Before
    public void createSchema() throws SQLException {
        try (Connection con = database.getDataSource().getConnection()) {
            database.getSchema().getDropScript().runScript(con);
            database.getSchema().getCreateScript().runScript(con);
            database.getSchema().getUpdateScript().runScript(con);
        }        
    }
    
    public static final FeedPath BASEBALL = FeedPath.valueOf("department/interest/baseball");
    public static final FeedPath SOCCER = FeedPath.valueOf("department/interest/soccer");
    public static FeedPath addId(FeedPath path) { return path.addId(Id.generate().toString()); }
    public static InputStream toStream(String data) { return new ByteArrayInputStream(data.getBytes()); }
    public static String toString(InputStream data) throws IOException { 
        ByteArrayOutputStream os = new ByteArrayOutputStream(); 
        OutputStreamConsumer.of(()->data).consume(os); 
        return os.toString(); 
    }
    
    @Test
    public void testCreateFeed() throws SQLException, InvalidPath {
        try (DatabaseInterface api = database.getInterface()) {
            Feed feed = api.createFeed(BASEBALL);
            api.commit();
            assertThat(feed.getId(), not(nullValue()));
            assertThat(feed.getId(), not(isEmptyString()));
        }
    }

    @Test
    public void testGetOrCreateFeed() throws SQLException, InvalidPath {
        try (DatabaseInterface api = database.getInterface()) {
            Feed feed = api.getOrCreateFeed(SOCCER);
            api.commit();
            assertThat(feed.getId(), not(nullValue()));
            assertThat(feed.getId(), not(isEmptyString()));
            Feed feed2 = api.getOrCreateFeed(SOCCER);
            api.commit();
            assertThat(feed2.getId(), equalTo(feed.getId()));
            assertThat(feed2.getName(), equalTo(feed.getName()));
        }
    }    
    
    @Test
    public void testMessageRoundtrip() throws SQLException, InvalidPath, IOException, InterruptedException {
        try (DatabaseInterface api = database.getInterface()) {
            Feed feed = api.createFeed(BASEBALL);
            Message testMessage = new MessageImpl(addId(BASEBALL), "testuser", Instant.now(), JsonObject.EMPTY_JSON_OBJECT, toStream("abc123"), -1, false);
            api.createMessage(Id.of(feed.getId()), testMessage);
            api.commit();
            Thread.sleep(1);
            try (Stream<Message> results = api.getMessages(BASEBALL, Instant.EPOCH, Instant.now())) {
                Message[] messages = results.toArray(Message[]::new);
                assertThat(messages, arrayWithSize(1));
                assertThat(messages[0], equalTo(testMessage));
                assertThat(toString(messages[0].getData()), equalTo("abc123"));
                assertThat(messages[0].getSender(), equalTo("testuser"));
            }
        }        
    }
}