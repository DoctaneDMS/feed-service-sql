/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.FeedExceptions;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author jonathan
 */
public class SQLFeedServiceMBean {
    private final SQLFeedService service;
    
    public SQLFeedServiceMBean(SQLFeedService service) {
        this.service = service;
    }
    
    public void dumpState() {
        try (PrintWriter out = new PrintWriter(System.err)) {
            service.dumpState(out);
        }         
    }
}
