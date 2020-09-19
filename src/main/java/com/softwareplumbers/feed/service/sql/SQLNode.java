/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 *
 * @author jonat
 */
public class SQLNode {
    
    public final Id id;
    public final Instant initTime;

    public SQLNode(Id id, Instant initTime) {
        this.id = id;
        this.initTime = initTime;
    }
    
    @Override
    public boolean equals(Object other) {
        return other instanceof SQLNode 
            && Objects.equals(id, ((SQLNode)other).id) 
            && Objects.equals(initTime, ((SQLNode)other).initTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, initTime);
    }
    
}
