/** (C) Copyright 2010 Hal Hildebrand, All Rights Reserved
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
package com.hellblazer.slp.anubis;

import static com.hellblazer.slp.ServiceScope.SERVICE_TYPE;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.smartfrog.services.anubis.BasicConfiguration;
import org.smartfrog.services.anubis.partition.test.colors.ColorAllocator;
import org.smartfrog.services.anubis.partition.test.mainconsole.Controller;
import org.smartfrog.services.anubis.partition.test.mainconsole.ControllerConfiguration;
import org.smartfrog.services.anubis.partition.test.mainconsole.NodeData;
import org.smartfrog.services.anubis.partition.util.Identity;
import org.smartfrog.services.anubis.partition.views.BitView;
import org.smartfrog.services.anubis.partition.views.View;
import org.smartfrog.services.anubis.partition.wire.msg.HeartbeatMsg;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.RandomBasedGenerator;
import com.hellblazer.anubis.annotations.DeployedPostProcessor;
import com.hellblazer.slp.InvalidSyntaxException;
import com.hellblazer.slp.ServiceEvent;
import com.hellblazer.slp.ServiceEvent.EventType;
import com.hellblazer.slp.ServiceListener;
import com.hellblazer.slp.ServiceScope;
import com.hellblazer.slp.ServiceURL;

/**
 * 
 * Functionally test the scope across multiple members in different failure
 * scenarios.
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
public class EndToEndTest extends TestCase {
    static class Event {
        final EventType type;
        final UUID registration;
        final ServiceURL url;
        final Map<String, Object> properties;

        Event(ServiceEvent event) {
            type = event.getType();
            url = event.getReference().getUrl();
            properties = new HashMap<String, Object>(
                                                     event.getReference().getProperties());
            registration = ((ServiceReferenceImpl) event.getReference()).getRegistration();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Event other = (Event) obj;
            if (registration == null) {
                if (other.registration != null) {
                    return false;
                }
            } else if (!registration.equals(other.registration)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                     + (registration == null ? 0 : registration.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "Event [type=" + type + ", registration=" + registration
                   + ", url=" + url + ", properties=" + properties + "]";
        }
    }

    static class Listener implements ServiceListener {
        int member;
        CountDownLatch latch;
        ApplicationContext context;
        Set<Event> events = new CopyOnWriteArraySet<EndToEndTest.Event>();

        Listener(ApplicationContext context) {
            this.context = context;
            member = context.getBean(Identity.class).id;
        }

        @Override
        public void serviceChanged(ServiceEvent event) {
            log.fine("updated <" + member + "> with: " + event);
            Event marked = new Event(event);
            if (events.add(marked)) {
                if (latch != null) {
                    latch.countDown();
                }
            } else {
                System.err.println("recevied duplicate: " + marked);
            }
        }

        void register(String query) throws BeansException,
                                   InvalidSyntaxException {
            context.getBean(ServiceScope.class).addServiceListener(this, query);
        }

        void unregister() {
            context.getBean(ServiceScope.class).removeServiceListener(this);
        }
    }

    static class MyController extends Controller {
        @Override
        protected NodeData createNode(HeartbeatMsg hb) {
            return new Node(hb, colorAllocator, this, headless);
        }

    }

    @Configuration
    static class MyControllerConfig extends ControllerConfiguration {
        @Override
        @Bean
        public DeployedPostProcessor deployedPostProcessor() {
            return new DeployedPostProcessor();
        }

        @Override
        public int heartbeatGroupTTL() {
            return 0;
        }

        @Override
        public int magic() {
            try {
                return Identity.getMagicFromLocalIpAddress();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected Controller constructController() {
            return new MyController();
        }

        @Override
        protected boolean headless() {
            return true;
        }

    }

    static class Node extends NodeData {
        boolean initial = true;
        CyclicBarrier barrier = INITIAL_BARRIER;
        int cardinality = CONFIGS.length;
        boolean interrupted = false;
        boolean barrierBroken = false;

        public Node(HeartbeatMsg hb, ColorAllocator colorAllocator,
                    Controller controller, boolean headless) {
            super(hb, colorAllocator, controller, headless);
        }

        @Override
        protected void partitionNotification(View partition, int leader) {
            log.fine("Partition notification: " + partition);
            super.partitionNotification(partition, leader);
            if (partition.isStable() && partition.cardinality() == cardinality) {
                interrupted = false;
                barrierBroken = false;
                Thread testThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            barrier.await();
                        } catch (InterruptedException e) {
                            interrupted = true;
                            return;
                        } catch (BrokenBarrierException e) {
                            barrierBroken = true;
                        }
                    }
                }, "Stability test thread for: " + getIdentity());
                testThread.setDaemon(true);
                testThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        e.printStackTrace();
                    }
                });
                testThread.start();
            }
        }
    }

    @Configuration
    static class node0 extends slpConfig {
        @Override
        public int node() {
            return 0;
        }
    }

    @Configuration
    static class node1 extends slpConfig {
        @Override
        public int node() {
            return 1;
        }
    }

    @Configuration
    static class node2 extends slpConfig {
        @Override
        public int node() {
            return 2;
        }
    }

    @Configuration
    static class node3 extends slpConfig {
        @Override
        public int node() {
            return 3;
        }
    }

    @Configuration
    static class node4 extends slpConfig {
        @Override
        public int node() {
            return 4;
        }
    }

    @Configuration
    static class node5 extends slpConfig {
        @Override
        public int node() {
            return 5;
        }
    }

    @Configuration
    static class node6 extends slpConfig {
        @Override
        public int node() {
            return 6;
        }
    }

    @Configuration
    static class node7 extends slpConfig {
        @Override
        public int node() {
            return 7;
        }
    }

    @Configuration
    static class node8 extends slpConfig {
        @Override
        public int node() {
            return 8;
        }
    }

    @Configuration
    static class node9 extends slpConfig {
        @Override
        public int node() {
            return 9;
        }
    }

    static class slpConfig extends BasicConfiguration {

        @Bean
        public ServiceScope anubisScope() {
            return new AnubisScope(stateName(), locator(),
                                   Executors.newSingleThreadExecutor(),
                                   uuidGenerator());
        }

        @Override
        public int getMagic() {
            try {
                return Identity.getMagicFromLocalIpAddress();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int heartbeatGroupTTL() {
            return 0;
        }

        protected String stateName() {
            return "Test Scope";
        }

        protected NoArgGenerator uuidGenerator() {
            return new RandomBasedGenerator(RANDOM);
        }
    }

    private static final Logger log = Logger.getLogger(EndToEndTest.class.getCanonicalName());
    static final Random RANDOM = new Random(666);
    static CyclicBarrier INITIAL_BARRIER;
    final static Class<?>[] CONFIGS = { node0.class, node1.class, node2.class,
                                       node3.class, node4.class, node5.class,
                                       node6.class, node7.class, node8.class,
                                       node9.class };

    ConfigurableApplicationContext controllerContext;
    List<ConfigurableApplicationContext> memberContexts;
    MyController controller;
    List<Node> partition;

    public void testSmoke() throws Exception {
        String memberIdKey = "test.member.id";
        ServiceURL url = new ServiceURL("service:http://foo.bar/drink-me");
        List<Listener> listeners = new ArrayList<Listener>();
        for (ApplicationContext context : memberContexts) {
            CountDownLatch latch = new CountDownLatch(memberContexts.size());
            Listener listener = new Listener(context);
            listener.latch = latch;
            listeners.add(listener);
            listener.register(getQuery("*"));
        }
        for (ApplicationContext context : memberContexts) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put(memberIdKey, context.getBean(Identity.class).id);
            context.getBean(ServiceScope.class).register(url, properties);
        }
        for (Listener listener : listeners) {
            assertTrue("listener <" + listener.member
                               + "> has not received all notifications",
                       listener.latch.await(30, TimeUnit.SECONDS));
            assertEquals(listeners.size(), listener.events.size());
            HashSet<Integer> sent = new HashSet<Integer>();
            for (Event event : listener.events) {
                assertEquals(EventType.REGISTERED, event.type);
                assertEquals(url, event.url);
                sent.add((Integer) event.properties.get(memberIdKey));
                assertEquals(event.properties.get(AnubisScope.MEMBER_IDENTITY),
                             event.properties.get(memberIdKey));
            }
            assertEquals(listeners.size(), sent.size());
        }
    }

    public void testSymmetricPartition() throws Exception {
        int minorPartitionSize = CONFIGS.length / 2;
        BitView A = new BitView();
        CyclicBarrier barrierA = new CyclicBarrier(minorPartitionSize + 1);
        List<Node> partitionA = new ArrayList<Node>();

        CyclicBarrier barrierB = new CyclicBarrier(minorPartitionSize + 1);
        List<Node> partitionB = new ArrayList<Node>();

        int i = 0;
        for (Node member : partition) {
            if (i++ % 2 == 0) {
                partitionB.add(member);
                member.barrier = barrierA;
                member.cardinality = minorPartitionSize;
                A.add(member.getIdentity());
            } else {
                partitionA.add(member);
                member.barrier = barrierB;
                member.cardinality = minorPartitionSize;
            }
        }

        String memberIdKey = "test.member.id";
        String roundKey = "test.round";
        ServiceURL url = new ServiceURL("service:http://foo.bar/drink-me");
        List<Listener> listeners = new ArrayList<Listener>();
        for (ApplicationContext context : memberContexts) {
            CountDownLatch latch = new CountDownLatch(memberContexts.size() * 2);
            Listener listener = new Listener(context);
            listener.latch = latch;
            listeners.add(listener);
            listener.register(getQuery("*"));
        }
        for (ApplicationContext context : memberContexts) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put(memberIdKey, context.getBean(Identity.class).id);
            properties.put(roundKey, 1);
            context.getBean(ServiceScope.class).register(url, properties);
        }

        log.info("symmetric partitioning: " + A);
        controller.symPartition(A);
        log.info("Awaiting stabilty of minor partition A");
        barrierA.await(60, TimeUnit.SECONDS);
        log.info("Awaiting stabilty of minor partition B");
        barrierB.await(60, TimeUnit.SECONDS);

        for (ApplicationContext context : memberContexts) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put(memberIdKey, context.getBean(Identity.class).id);
            properties.put(roundKey, 2);
            context.getBean(ServiceScope.class).register(url, properties);
        }

        // reform
        CyclicBarrier barrier = new CyclicBarrier(CONFIGS.length + 1);
        for (Node node : partition) {
            node.barrier = barrier;
            node.cardinality = CONFIGS.length;
        }

        controller.clearPartitions();
        log.info("Awaiting stabilty of reformed major partition");
        barrier.await(30, TimeUnit.SECONDS);

        for (Listener listener : listeners) {
            assertTrue("listener <" + listener.member
                               + "> has not received all notifications",
                       listener.latch.await(30, TimeUnit.SECONDS));
            assertEquals("listener <"
                                 + listener.member
                                 + "> has received more notifications than expected ",
                         listeners.size() * 2, listener.events.size());
            HashSet<Integer> sent = new HashSet<Integer>();
            for (Event event : listener.events) {
                assertEquals(EventType.REGISTERED, event.type);
                assertEquals(url, event.url);
                sent.add((Integer) event.properties.get(memberIdKey));
                assertEquals(event.properties.get(AnubisScope.MEMBER_IDENTITY),
                             event.properties.get(memberIdKey));
            }
            assertEquals("listener <" + listener.member
                         + "> did not receive messages from all members: "
                         + sent, listeners.size(), sent.size());
        }
    }

    public void testAsymmetricPartition() throws Exception {
        int minorPartitionSize = CONFIGS.length / 2;
        BitView A = new BitView();
        CyclicBarrier barrierA = new CyclicBarrier(minorPartitionSize + 1);
        List<Node> partitionA = new ArrayList<Node>();

        CyclicBarrier barrierB = new CyclicBarrier(minorPartitionSize + 1);
        List<Node> partitionB = new ArrayList<Node>();

        int i = 0;
        for (Node member : partition) {
            if (i++ % 2 == 0) {
                partitionB.add(member);
                member.barrier = barrierA;
                member.cardinality = minorPartitionSize;
                A.add(member.getIdentity());
            } else {
                partitionA.add(member);
                member.barrier = barrierB;
                member.cardinality = minorPartitionSize;
            }
        }

        String memberIdKey = "test.member.id";
        String roundKey = "test.round";
        ServiceURL url = new ServiceURL("service:http://foo.bar/drink-me");
        List<Listener> listeners = new ArrayList<Listener>();
        for (ApplicationContext context : memberContexts) {
            CountDownLatch latch = new CountDownLatch(memberContexts.size() * 2);
            Listener listener = new Listener(context);
            listener.latch = latch;
            listeners.add(listener);
            listener.register(getQuery("*"));
        }
        for (ApplicationContext context : memberContexts) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put(memberIdKey, context.getBean(Identity.class).id);
            properties.put(roundKey, 1);
            context.getBean(ServiceScope.class).register(url, properties);
        }

        log.info("asymmetric partitioning: " + A);
        controller.asymPartition(A);
        log.info("Awaiting stabilty of minor partition A");
        barrierA.await(60, TimeUnit.SECONDS);

        for (ApplicationContext context : memberContexts) {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put(memberIdKey, context.getBean(Identity.class).id);
            properties.put(roundKey, 2);
            context.getBean(ServiceScope.class).register(url, properties);
        }

        // reform
        CyclicBarrier barrier = new CyclicBarrier(CONFIGS.length + 1);
        for (Node node : partition) {
            node.barrier = barrier;
            node.cardinality = CONFIGS.length;
        }

        controller.clearPartitions();
        log.info("Awaiting stabilty of reformed major partition");
        barrier.await(30, TimeUnit.SECONDS);

        for (Listener listener : listeners) {
            assertTrue("listener <" + listener.member
                               + "> has not received all notifications",
                       listener.latch.await(30, TimeUnit.SECONDS));
            assertEquals("listener <"
                                 + listener.member
                                 + "> has received more notifications than expected ",
                         listeners.size() * 2, listener.events.size());
            HashSet<Integer> sent = new HashSet<Integer>();
            for (Event event : listener.events) {
                assertEquals(EventType.REGISTERED, event.type);
                assertEquals(url, event.url);
                sent.add((Integer) event.properties.get(memberIdKey));
                assertEquals(event.properties.get(AnubisScope.MEMBER_IDENTITY),
                             event.properties.get(memberIdKey));
            }
            assertEquals("listener <" + listener.member
                         + "> did not receive messages from all members: "
                         + sent, listeners.size(), sent.size());
        }
    }

    private List<ConfigurableApplicationContext> createMembers() {
        ArrayList<ConfigurableApplicationContext> contexts = new ArrayList<ConfigurableApplicationContext>();
        for (Class<?> config : CONFIGS) {
            contexts.add(new AnnotationConfigApplicationContext(config));
        }
        return contexts;
    }

    private String getQuery(String serviceType) {
        return "(" + SERVICE_TYPE + "=" + serviceType + ")";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        log.info("Setting up initial partition");
        INITIAL_BARRIER = new CyclicBarrier(CONFIGS.length + 1);
        controllerContext = new AnnotationConfigApplicationContext(
                                                                   MyControllerConfig.class);
        memberContexts = createMembers();
        controller = (MyController) controllerContext.getBean(Controller.class);
        log.info("Awaiting initial partition stability");
        INITIAL_BARRIER.await(120, TimeUnit.SECONDS);
        log.info("Initial partition stabile");
        partition = new ArrayList<Node>();
        for (ConfigurableApplicationContext context : memberContexts) {
            partition.add((Node) controller.getNode(context.getBean(Identity.class)));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (controllerContext != null) {
            try {
                controllerContext.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        controllerContext = null;
        if (memberContexts != null) {
            for (ConfigurableApplicationContext context : memberContexts) {
                try {
                    context.close();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        memberContexts = null;
        controller = null;
        partition = null;
        INITIAL_BARRIER = null;
    }
}