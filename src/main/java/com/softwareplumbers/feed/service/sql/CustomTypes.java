package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.common.sql.CompositeType;
import com.softwareplumbers.common.sql.CustomType;
import com.softwareplumbers.common.sql.FluentStatement;
import com.softwareplumbers.feed.FeedPath;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
public class CustomTypes {
    
    public static final CustomType<Id> ID = new CustomType<Id>() {
        
        @Override
        public void set (PreparedStatement statement, int index, Id value) throws SQLException {
            if (value == null) statement.setNull(index,  java.sql.Types.BINARY);
            else statement.setBytes(index, value.asBytes());            
        }
        
        @Override
        public String format(Id value) {
            return value.toString();
        }
    };
    
    public static final CompositeType<FeedPath> PATH = CustomTypes::setFeedPath;
    
    public static final FluentStatement setFeedPath(FluentStatement fluentStatement, String name, FeedPath path) {
        if (path.isEmpty()) return fluentStatement.set(ID, name, Id.ROOT_ID);
        switch (path.part.type) {
            case FEED:
                return fluentStatement.set(PATH, "parent." + name, path.parent).set(name, path.part.getName().get()).set(name + ".version", path.part.getVersion().orElse(DatabaseInterface.NULL_VERSION_VALUE)); 
            case MESSAGEID:
                return fluentStatement.set(PATH, "parent." + name, path.parent).set(name, path.part.getId().get()); 
            default:
                return fluentStatement.set(PATH, "parent." + name, path.parent);
        }
    }    
}
