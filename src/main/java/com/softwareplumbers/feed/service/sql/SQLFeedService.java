/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.Feed;
import com.softwareplumbers.feed.FeedExceptions.BaseRuntimeException;
import com.softwareplumbers.feed.FeedExceptions.InvalidPath;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.MessageIterator;
import com.softwareplumbers.feed.impl.AbstractFeedService;
import java.sql.SQLException;
import java.time.Instant;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
public class SQLFeedService extends AbstractFeedService {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(SQLFeedService.class);
    
    private final MessageDatabase database;
    
    public SQLFeedService(MessageDatabase database, long poolSize, int bucketSize) {
        super(poolSize, bucketSize);
        this.database = database;
    }

    @Override
    protected MessageIterator syncFromBackEnd(FeedPath path, Instant from, Instant to) {
        LOG.entry(path, from, to);
        try (DatabaseInterface dbi = database.getInterface()) {
            return LOG.exit(MessageIterator.of(dbi.getMessages(path, from, to)));
        } catch (SQLException sqe) {
            throw LOG.throwing(new RuntimeException(sqe));
        }
    }

    @Override
    protected void startBackEndListener(FeedPath path, Instant from) {
        LOG.entry(path, from);
        try (DatabaseInterface dbi = database.getInterface()) {
            Feed feed = dbi.getOrCreateFeed(path);
            dbi.commit();
            listen(path, from, messages->writeToDatabase(feed, from, messages));
        } catch (SQLException sqe) {
            LOG.error("Critical error - unable to start database listener");
            LOG.catching(sqe);
            throw LOG.throwing(new RuntimeException(sqe));
        } catch (InvalidPath e) {
            LOG.error("Critical error - unable to start database listener");
            LOG.catching(e);
            throw LOG.throwing(new BaseRuntimeException(e));
        }
        LOG.exit();
    }    
    
    void writeToDatabase(Feed feed, Instant from, MessageIterator messages) {
        LOG.entry(feed, from, messages);
        Instant lastWritten = from;
        int count = 0;
        try (DatabaseInterface dbi = database.getInterface()) {
            while (messages.hasNext()) {
                Message message = messages.next();
                try {
                    dbi.createMessage(Id.of(feed.getId()), message);
                    dbi.commit();
                } catch (Exception ex) {
                    LOG.error("Can't persist message {} {}", message.getName(), message.getTimestamp());
                    LOG.catching(ex);
                }
                lastWritten = message.getTimestamp();
                count++;
            }
        } catch (SQLException sqe) {
            LOG.error("Catastropic failure {}", sqe);
            LOG.catching(sqe);
        } finally {
            LOG.debug("Wrote {} messages for {} from {} to {}", count, feed, from, lastWritten);
            Instant next = lastWritten; // It's this kind of thing that really pisses me off about java
            feed.listen(this, next, msg->writeToDatabase(feed, next, msg));
        }
        LOG.exit();
    }
}
