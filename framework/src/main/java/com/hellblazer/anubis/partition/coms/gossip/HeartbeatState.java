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
package com.hellblazer.anubis.partition.coms.gossip;

import static com.hellblazer.anubis.partition.coms.gossip.Communications.readInetAddress;
import static com.hellblazer.anubis.partition.coms.gossip.Communications.writeInetAddress;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.smartfrog.services.anubis.partition.util.Identity;
import org.smartfrog.services.anubis.partition.util.NodeIdSet;
import org.smartfrog.services.anubis.partition.views.BitView;
import org.smartfrog.services.anubis.partition.views.View;
import org.smartfrog.services.anubis.partition.wire.msg.Heartbeat;
import org.smartfrog.services.anubis.partition.wire.msg.HeartbeatMsg;

/**
 * The heartbeat state replicated by the gossip protocol
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
public class HeartbeatState implements Heartbeat {

    public static HeartbeatState toHeartbeatState(Heartbeat heartbeat) {
        if (heartbeat instanceof HeartbeatState) {
            return (HeartbeatState) heartbeat;
        }
        return new HeartbeatState(heartbeat);
    }

    private Identity          candidate;
    private NodeIdSet         msgLinks;
    private boolean           preferred;
    private Identity          sender;
    private InetSocketAddress senderAddress;
    private boolean           stable        = false;
    private InetSocketAddress testInterface;
    private NodeIdSet         view;
    private long              viewNumber    = -1;
    private long              viewTimeStamp = View.undefinedTimeStamp;

    private byte[]            binaryCache;

    public HeartbeatState(ByteBuffer buffer) throws UnknownHostException {
        binaryCache = new byte[GossipMessages.HEARTBEAT_STATE_BYTE_SIZE];
        buffer.get(binaryCache);
        ByteBuffer msg = ByteBuffer.wrap(binaryCache);
        candidate = new Identity(msg);
        msgLinks = new NodeIdSet(msg);
        preferred = msg.get() > 0 ? true : false;
        sender = new Identity(msg);
        senderAddress = readInetAddress(msg);
        stable = msg.get() > 0 ? true : false;
        testInterface = readInetAddress(msg);
        view = new NodeIdSet(msg);
        viewNumber = msg.getLong();
        viewTimeStamp = msg.getLong();
    }

    public HeartbeatState(Heartbeat heartbeat) {
        candidate = heartbeat.getCandidate();
        msgLinks = heartbeat.getMsgLinks();
        preferred = heartbeat.isPreferred();
        sender = heartbeat.getSender();
        senderAddress = heartbeat.getSenderAddress();
        setView(heartbeat.getView());
        setViewNumber(heartbeat.getViewNumber());
        fillCache();
    }

    public HeartbeatState(Identity candidate, NodeIdSet msgLinks,
                          boolean preferred, Identity sender,
                          InetSocketAddress senderAddress, boolean stable,
                          InetSocketAddress testInterface, NodeIdSet view,
                          long viewNumber, long viewTimestamp) {
        this.candidate = candidate;
        this.msgLinks = msgLinks;
        this.preferred = preferred;
        this.sender = sender;
        this.senderAddress = senderAddress;
        this.stable = stable;
        this.testInterface = testInterface;
        this.view = view;
        this.viewNumber = viewNumber;
        viewTimeStamp = viewTimestamp;
        fillCache();
    }

    public HeartbeatState(InetSocketAddress address) {
        candidate = new Identity(-1, -1, -1);
        msgLinks = new NodeIdSet(1);
        sender = new Identity(-1, -1, -1);
        senderAddress = address;
        view = new NodeIdSet(1);
        fillCache();
    }

    protected HeartbeatState(InetSocketAddress address, Identity id) {
        sender = id;
        senderAddress = address;
        view = new NodeIdSet(1);
        candidate = new Identity(-1, -1, -1);
        msgLinks = new NodeIdSet(1);
        fillCache();
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
        HeartbeatState other = (HeartbeatState) obj;
        if (candidate == null) {
            if (other.candidate != null) {
                return false;
            }
        } else if (!candidate.equals(other.candidate)) {
            return false;
        }
        if (msgLinks == null) {
            if (other.msgLinks != null) {
                return false;
            }
        } else if (!msgLinks.equals(other.msgLinks)) {
            return false;
        }
        if (preferred != other.preferred) {
            return false;
        }
        if (sender == null) {
            if (other.sender != null) {
                return false;
            }
        } else if (!sender.equals(other.sender)) {
            return false;
        }
        if (senderAddress == null) {
            if (other.senderAddress != null) {
                return false;
            }
        } else if (!senderAddress.equals(other.senderAddress)) {
            return false;
        }
        if (stable != other.stable) {
            return false;
        }
        if (testInterface == null) {
            if (other.testInterface != null) {
                return false;
            }
        } else if (!testInterface.equals(other.testInterface)) {
            return false;
        }
        if (view == null) {
            if (other.view != null) {
                return false;
            }
        } else if (!view.equals(other.view)) {
            return false;
        }
        if (viewNumber != other.viewNumber) {
            return false;
        }
        if (viewTimeStamp != other.viewTimeStamp) {
            return false;
        }
        return true;
    }

    @Override
    public Identity getCandidate() {
        return candidate;
    }

    public long getEpoch() {
        return sender.epoch;
    }

    @Override
    public NodeIdSet getMsgLinks() {
        return msgLinks;
    }

    @Override
    public Identity getSender() {
        return sender;
    }

    @Override
    public InetSocketAddress getSenderAddress() {
        return senderAddress;
    }

    @Override
    public InetSocketAddress getTestInterface() {
        return testInterface;
    }

    @Override
    public long getTime() {
        return viewTimeStamp;
    }

    @Override
    public View getView() {
        return new BitView(stable, view, viewTimeStamp);
    }

    @Override
    public long getViewNumber() {
        return viewNumber;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + (candidate == null ? 0 : candidate.hashCode());
        result = prime * result + (msgLinks == null ? 0 : msgLinks.hashCode());
        result = prime * result + (preferred ? 1231 : 1237);
        result = prime * result + (sender == null ? 0 : sender.hashCode());
        result = prime * result
                 + (senderAddress == null ? 0 : senderAddress.hashCode());
        result = prime * result + (stable ? 1231 : 1237);
        result = prime * result
                 + (testInterface == null ? 0 : testInterface.hashCode());
        result = prime * result + (view == null ? 0 : view.hashCode());
        result = prime * result + (int) (viewNumber ^ viewNumber >>> 32);
        result = prime * result + (int) (viewTimeStamp ^ viewTimeStamp >>> 32);
        return result;
    }

    @Override
    public boolean isPreferred() {
        return preferred;
    }

    public boolean record(HeartbeatState remoteState) {
        assert sender.equalId(remoteState.sender);
        return remoteState.sender.epoch > sender.epoch
               || remoteState.viewNumber > viewNumber;
    }

    @Override
    public void setCandidate(Identity id) {
        candidate = id;
    }

    @Override
    public void setIsPreferred(boolean preferred) {
        this.preferred = preferred;
    }

    @Override
    public void setMsgLinks(NodeIdSet ml) {
        msgLinks = ml;
    }

    @Override
    public void setTestInterface(InetSocketAddress address) {
        testInterface = address;
    }

    @Override
    public void setTime(long t) {
        viewTimeStamp = t;
    }

    @Override
    public void setView(View v) {
        view = v.toBitSet();
        stable = v.isStable();
        viewTimeStamp = v.getTimeStamp();
    }

    @Override
    public void setViewNumber(long n) {
        viewNumber = n;
    }

    @Override
    public Heartbeat toClose() {
        return new HeartbeatMsg(this).toClose();
    }

    @Override
    public String toString() {
        return "HeartbeatState [" + sender + " | " + senderAddress
               + ", stable=" + stable + "]";
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.put(binaryCache);
    }

    private void fillCache() {
        binaryCache = new byte[GossipMessages.HEARTBEAT_STATE_BYTE_SIZE];
        ByteBuffer msg = ByteBuffer.wrap(binaryCache);
        candidate.writeTo(msg);
        msgLinks.writeTo(msg);
        if (preferred) {
            msg.put((byte) 1);
        } else {
            msg.put((byte) 0);
        }
        sender.writeTo(msg);
        writeInetAddress(senderAddress, msg);
        if (stable) {
            msg.put((byte) 1);
        } else {
            msg.put((byte) 0);
        }
        writeInetAddress(testInterface, msg);
        view.writeTo(msg);
        msg.putLong(viewNumber);
        msg.putLong(viewTimeStamp);
    }
}
