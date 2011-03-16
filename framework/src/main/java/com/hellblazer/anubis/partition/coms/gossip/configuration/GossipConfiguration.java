/** 
 * (C) Copyright 2010 Hal Hildebrand, All Rights Reserved
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.hellblazer.anubis.partition.coms.gossip.configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.smartfrog.services.anubis.locator.AnubisLocator;
import org.smartfrog.services.anubis.locator.Locator;
import org.smartfrog.services.anubis.partition.PartitionManager;
import org.smartfrog.services.anubis.partition.comms.IOConnectionServerFactory;
import org.smartfrog.services.anubis.partition.comms.multicast.HeartbeatCommsFactory;
import org.smartfrog.services.anubis.partition.comms.nonblocking.MessageNioServerFactory;
import org.smartfrog.services.anubis.partition.protocols.heartbeat.HeartbeatProtocolFactory;
import org.smartfrog.services.anubis.partition.protocols.leader.LeaderProtocolFactory;
import org.smartfrog.services.anubis.partition.protocols.partitionmanager.ConnectionSet;
import org.smartfrog.services.anubis.partition.protocols.partitionmanager.PartitionProtocol;
import org.smartfrog.services.anubis.partition.test.node.TestMgr;
import org.smartfrog.services.anubis.partition.util.Epoch;
import org.smartfrog.services.anubis.partition.util.Identity;
import org.smartfrog.services.anubis.partition.wire.security.NoSecurityImpl;
import org.smartfrog.services.anubis.partition.wire.security.WireSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hellblazer.anubis.annotations.DeployedPostProcessor;
import com.hellblazer.anubis.basiccomms.nio.SocketOptions;
import com.hellblazer.anubis.partition.coms.gossip.Communications;
import com.hellblazer.anubis.partition.coms.gossip.Gossip;
import com.hellblazer.anubis.partition.coms.gossip.PhiTimedProtocolFactory;
import com.hellblazer.anubis.partition.coms.gossip.SystemView;
import static java.util.Arrays.asList;

/**
 * Basic gossip based discovery/replication Anubis configuration.
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 *
 */
@Configuration
public class GossipConfiguration {

    @Bean
    public Communications communications() throws IOException {
        return new Communications("Gossip Endpoint Handler for "
                                  + partitionIdentity(), gossipEndpoint(),
                                  socketOptions(),
                                  Executors.newFixedThreadPool(3),
                                  Executors.newSingleThreadExecutor());

    }

    @Bean
    public ConnectionSet connectionSet() throws Exception {
        return new ConnectionSet(contactAddress(), partitionIdentity(),
                                 heartbeatCommsFactory(),
                                 ioConnectionServerFactory(),
                                 leaderProtocolFactory(),
                                 heartbeatProtocolFactory(),
                                 partitionProtocol(), heartbeatInterval(),
                                 heartbeatTimeout());
    }

    public InetAddress contactHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    @Bean
    public DeployedPostProcessor deployedPostProcessor() {
        return new DeployedPostProcessor();
    }

    @Bean
    public Epoch epoch() {
        return new Epoch();
    }

    @Bean
    public Gossip gossip() throws IOException {
        return new Gossip(systemView(), new SecureRandom(),
                          phiConvictionThreshold(), communications(),
                          gossipInterval(), gossipIntervalTimeUnit());
    }

    @Bean
    public AnubisLocator locator() {
        Locator locator = new Locator();
        locator.setIdentity(partitionIdentity());
        locator.setPartition(partition());
        locator.setHeartbeatInterval(heartbeatInterval());
        locator.setHeartbeatTimeout(heartbeatTimeout());
        return locator;
    }

    @Bean
    public PartitionManager partition() {
        PartitionManager partition = new PartitionManager(partitionIdentity());
        return partition;
    }

    @Bean
    public Identity partitionIdentity() {
        return new Identity(getMagic(), node(), epoch().longValue());
    }

    @Bean
    public PartitionProtocol partitionProtocol() {
        PartitionProtocol protocol = new PartitionProtocol();
        protocol.setPartitionMgr(partition());
        protocol.setIdentity(partitionIdentity());
        return protocol;
    }

    @Bean
    public SystemView systemView() throws IOException {
        return new SystemView(new SecureRandom(),
                              communications().getLocalAddress(), seedHosts(),
                              quarantineDelay(), unreachableNodeDelay());
    }

    @Bean
    public TestMgr testMgr() throws Exception {
        TestMgr mgr = new TestMgr(contactHost().getCanonicalHostName(),
                                  contactPort(), partition(), node());
        mgr.setConnectionAddress(contactAddress());
        mgr.setConnectionSet(connectionSet());
        mgr.setIdentity(partitionIdentity());
        mgr.setTestable(getTestable());
        return mgr;
    }

    @Bean
    public WireSecurity wireSecurity() {
        return new NoSecurityImpl();
    }

    protected InetSocketAddress contactAddress() throws UnknownHostException {
        return new InetSocketAddress(contactHost(), contactPort());
    }

    protected int contactPort() {
        return 0;
    }

    protected int getMagic() {
        return 12345;
    }

    protected boolean getTestable() {
        return true;
    }

    protected InetSocketAddress gossipEndpoint() throws UnknownHostException {
        return new InetSocketAddress(contactHost(), 0);
    }

    protected int gossipInterval() {
        return 1;
    }

    protected TimeUnit gossipIntervalTimeUnit() {
        return TimeUnit.SECONDS;
    }

    protected HeartbeatCommsFactory heartbeatCommsFactory() throws IOException {
        return gossip();
    }

    protected long heartbeatInterval() {
        return 2000L;
    }

    @Bean
    public HeartbeatProtocolFactory heartbeatProtocolFactory()
                                                              throws IOException {
        return new PhiTimedProtocolFactory(gossip());
    }

    protected long heartbeatTimeout() {
        return 3L;
    }

    protected IOConnectionServerFactory ioConnectionServerFactory()
                                                                   throws Exception {
        MessageNioServerFactory factory = new MessageNioServerFactory();
        factory.setWireSecurity(wireSecurity());
        return factory;
    }

    protected LeaderProtocolFactory leaderProtocolFactory() {
        return new LeaderProtocolFactory();
    }

    protected int node() {
        try {
            return Identity.getProcessUniqueId();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int phiConvictionThreshold() {
        return 11;
    }

    protected int quarantineDelay() {
        return 5000;
    }

    protected Collection<InetSocketAddress> seedHosts()
                                                       throws UnknownHostException {
        return asList(gossipEndpoint());
    }

    protected SocketOptions socketOptions() {
        return new SocketOptions();
    }

    protected int unreachableNodeDelay() {
        return 500000;
    }
}