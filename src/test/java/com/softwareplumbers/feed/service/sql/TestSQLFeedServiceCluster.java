/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.feed.service.sql;

import com.softwareplumbers.feed.Cluster;
import com.softwareplumbers.feed.TestCluster;
import java.io.PrintWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author jonathan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ClusterConfig.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TestSQLFeedServiceCluster extends TestCluster {
    
    @Autowired @Qualifier(value="remoteSimpleCluster")
    protected Cluster remoteCluster;
    
    
    @Test
    public void dumpRemoteCluster() {
        try (PrintWriter out = new PrintWriter(System.out)) {
            cluster.dumpState(out);
            remoteCluster.dumpState(out);
        }
    }
}
