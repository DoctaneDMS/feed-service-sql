/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.Feed;
import com.softwareplumbers.feed.FeedExceptions;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.TestFeedService;
import static com.softwareplumbers.feed.test.TestUtils.generateMessage;
import static com.softwareplumbers.feed.test.TestUtils.unsafeFeedPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author jonathan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { LocalConfig.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TestSQLFeedService extends TestFeedService {

    final FeedPath sqlUnsafePath = FeedPath.ROOT.add("ab$").add("cd&?%ef_'\"g");
    
    @Test
    public void testGetFeedInfoWithSQLUnsafePath() throws FeedExceptions.InvalidPath {
        FeedPath path = sqlUnsafePath;
        Message expectedReturn = post(generateMessage(path));
        Feed feed = service.getFeed(path);
        assertThat(feed.getName(), equalTo(path));
        assertThat(feed.getLastTimestamp().get(), equalTo(expectedReturn.getTimestamp()));
        assertThat(feed.getLastTimestamp(service).get(), equalTo(expectedReturn.getTimestamp()));
        assertThat(service.getLastTimestamp(path).get(), equalTo(expectedReturn.getTimestamp()));
    }     
    
    public void testGetFeedChildrenWithSQLUnsafePath() throws FeedExceptions.InvalidPath {
        FeedPath path = sqlUnsafePath;
        FeedPath childA = path.add("a");
        FeedPath childB = path.add("b");
        FeedPath grandChild = childA.add("c");
        
        post(generateMessage(childA));
        post(generateMessage(childB));
        post(generateMessage(grandChild));
        
        assertThat(service.getChildren(path).count(), equalTo(2L));
        assertThat(service.getFeed(path).getChildren(service).count(), equalTo(2L));
        
        assertThat(service.getChildren(childA).count(), equalTo(1L));
        assertThat(service.getFeed(childA).getChildren(service).count(), equalTo(1L));

        assertThat(service.getChildren(childB).count(), equalTo(0L));
        assertThat(service.getFeed(childB).getChildren(service).count(), equalTo(0L));

        service.getChildren(path).forEach(child->{
            assertThat(child.getName(), anyOf(equalTo(childA), equalTo(childB)));
        });
    }     
    
}
