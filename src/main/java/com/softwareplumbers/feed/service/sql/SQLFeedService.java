/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.FeedExceptions.InvalidPath;
import com.softwareplumbers.feed.FeedPath;
import com.softwareplumbers.feed.FeedService;
import com.softwareplumbers.feed.Message;
import com.softwareplumbers.feed.MessageIterator;
import com.softwareplumbers.feed.impl.buffer.BufferPool;
import com.softwareplumbers.feed.impl.buffer.MessageBuffer;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 *
 * @author jonathan
 */
public class SQLFeedService implements FeedService {
    
    private final BufferPool bufferPool;
    private final Map<FeedPath, MessageBuffer> feeds = new ConcurrentHashMap<>();
    private final int bucketSize;
    private MessageDatabase database;
    
    public SQLFeedService(MessageDatabase database, long poolSize, int bucketSize) {
        this.bufferPool = new BufferPool((int)poolSize);
        this.bucketSize = bucketSize;        
    }
    
    private MessageBuffer getBuffer(FeedPath path) {
        return feeds.computeIfAbsent(path, p->bufferPool.createBuffer(bucketSize));
    }

    @Override
    public void listen(FeedPath path, Instant from, Consumer<MessageIterator> callback) throws InvalidPath {
        if (path.isEmpty() || path.part.getId().isPresent()) throw new InvalidPath(path);
        getBuffer(path).getMessagesAfter(from, callback);
    }

    @Override
    public MessageIterator sync(FeedPath path, Instant from) throws InvalidPath {
        if (path.isEmpty() || path.part.getId().isPresent()) throw new InvalidPath(path);
        MessageBuffer buffer = getBuffer(path);
        if (buffer.firstTimestamp().map(ts->ts.compareTo(from) < 0).orElse(false)) {
            return buffer.getMessagesAfter(from);        
        } else {
            try (DatabaseInterface dbi = database.getInterface()) {
                return MessageIterator.of(dbi.getMessagesFrom(path, from));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }      
    }

    @Override
    public Message post(FeedPath path, Message message) throws InvalidPath {
        if (path.isEmpty() || path.part.getId().isPresent()) throw new InvalidPath(path);
        MessageBuffer buffer = getBuffer(path);
        synchronized(buffer) {
            if (buffer.isEmpty()) {
                Id feedId = createFeed(path);
                databaseSync(feedId, buffer.now(), buffer);
            } 
        }
        return buffer.addMessage(message);
    }
       
    private void databaseSync(Id feedId, Instant from, MessageBuffer buffer) {
        buffer.getMessagesAfter(from, iterator->{
            Instant lastWritten = from;
            try (DatabaseInterface dbi = database.getInterface()) {
                while (iterator.hasNext()) {
                    Message message = iterator.next();
                    dbi.createMessage(feedId, message);
                    dbi.commit();
                    lastWritten = message.getTimestamp();
                }
            } catch (SQLException sqe) {
                // Hmmm...
            } finally {
                iterator.close();
                databaseSync(feedId, lastWritten, buffer);
            }
        });
    }
    
    private Id createFeed(FeedPath path) throws InvalidPath {
        try (DatabaseInterface dbi = database.getInterface()) {
            Id feedId = dbi.getOrCreateFeed(path);
            dbi.commit();
            return feedId;
        } catch (SQLException e) {
            throw new RuntimeException("can't create feed", e);
        }
    }
    
}
