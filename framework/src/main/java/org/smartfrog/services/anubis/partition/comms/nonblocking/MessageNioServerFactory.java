/** (C) Copyright 1998-2005 Hewlett-Packard Development Company, LP

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

For more information: www.smartfrog.org

 */
package org.smartfrog.services.anubis.partition.comms.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.smartfrog.services.anubis.partition.comms.IOConnectionServer;
import org.smartfrog.services.anubis.partition.comms.IOConnectionServerFactory;
import org.smartfrog.services.anubis.partition.protocols.partitionmanager.ConnectionSet;
import org.smartfrog.services.anubis.partition.util.Identity;
import org.smartfrog.services.anubis.partition.wire.security.WireSecurity;

import com.hellblazer.pinkie.SocketOptions;

public class MessageNioServerFactory implements IOConnectionServerFactory {

    private final WireSecurity  wireSecurity;
    private final SocketOptions socketOptions;

    public MessageNioServerFactory(WireSecurity wireSecurity,
                                   SocketOptions socketOptions) {
        super();
        this.wireSecurity = wireSecurity;
        this.socketOptions = socketOptions;
    }

    @Override
    public IOConnectionServer create(InetSocketAddress address, Identity id,
                                     ConnectionSet cs) throws IOException {
        return new MessageNioServer(address, id, cs, wireSecurity,
                                    socketOptions);
    }
}
