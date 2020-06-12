/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

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
        service.dumpState();
    }
}
