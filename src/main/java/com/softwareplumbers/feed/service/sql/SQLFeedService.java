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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 *
 * @author jonathan
 */
public class SQLFeedService implements FeedService {
    
    private final BufferPool bufferPool;
    private final Map<FeedPath, MessageBuffer> feeds = new ConcurrentHashMap<>();
    private final long bufferFor;
    private final int bucketSize;
    private MessageDatabase database;
    
    public SQLFeedService(long poolSize, int bucketSize, long bufferFor, TimeUnit units) {
        this.bufferPool = new BufferPool((int)poolSize);
        this.bufferFor = units.toMillis(bufferFor);
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
    public Message post(Message msg) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
