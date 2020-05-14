/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 *
 * @author jonathan
 */
public class Id {
    
    public static final Id ROOT_ID = Id.of("00000000-0000-0000-0000-000000000000");
    
    private final String value;
    
    public Id(String value) {
        this.value = value;
    }
    
    public Id() {
        this.value = UUID.randomUUID().toString();
    }
    
    public byte[] asBytes() {
        byte[] data = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(data);
        UUID uuid = UUID.fromString(value);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return data;
    }
    
    public String toString() {
        return value;
    }
    
    public static Id of(String value) {
        return new Id(value);
    }
    
    public static Id of(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new Id(new UUID(firstLong, secondLong).toString());
    }
    
    public static Id generate() {
        return new Id();
    }
}
