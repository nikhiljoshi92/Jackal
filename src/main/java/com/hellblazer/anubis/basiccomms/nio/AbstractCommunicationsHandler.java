/** (C) Copyright 2010 Hal Hildebrand, all rights reserved.

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
 */
package com.hellblazer.anubis.basiccomms.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smartfrog.services.anubis.partition.wire.WireSizes;

/**
 * 
 * @author <a href="mailto:hal.hildebrand@gmail.com">Hal Hildebrand</a>
 * 
 */
abstract public class AbstractCommunicationsHandler implements WireSizes,
        CommunicationsHandler {
    private static enum State {
        INITIAL, HEADER, MESSAGE, ERROR, CLOSE;
    }

    private static final Logger log = Logger.getLogger(AbstractCommunicationsHandler.class.getCanonicalName());

    private volatile boolean open = true;
    private final SocketChannel channel;
    private ByteBuffer headerIn = ByteBuffer.wrap(new byte[HEADER_SIZE]);
    private ByteBuffer headerOut = ByteBuffer.wrap(new byte[HEADER_SIZE]);
    private ByteBuffer msgIn;
    private ByteBuffer msgOut;
    protected final ServerChannelHandler handler;
    private volatile State readState = State.INITIAL;
    private volatile State writeState = State.INITIAL;
    private final Semaphore writeGate = new Semaphore(1);

    public AbstractCommunicationsHandler(ServerChannelHandler handler,
                                         SocketChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    @Override
    public void close() {
        readState = writeState = State.CLOSE;
        open = false;
        try {
            channel.close();
        } catch (IOException e) {
        }
        handler.closeHandler(this);
    }

    public boolean connected() {
        return open;
    }

    @Override
    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public void handleAccept() {
        readState = State.INITIAL;
        writeState = State.INITIAL;
        handler.selectForRead(this);
    }

    @Override
    public void handleRead() {
        switch (readState) {
            case INITIAL: {
                headerIn.clear();
                readState = State.HEADER;
                readHeader();
                break;
            }
            case HEADER: {
                readHeader();
                break;
            }
            case ERROR: {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("In error, ignoring read ready");
                }
                break; // Don't read while in error
            }
            case MESSAGE: {
                readMessage();
                break;
            }
            case CLOSE: {
                break; // ignore
            }
            default:
                throw new IllegalStateException("Invalid read state");
        }
    }

    @Override
    public void handleWrite() {
        switch (writeState) {
            case INITIAL: {
                throw new IllegalStateException("Should never be initial state");
            }
            case HEADER: {
                writeHeader();
                break;
            }
            case ERROR: {
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("In error, ignoring write ready");
                }
                break; // Don't write while in error
            }
            case MESSAGE: {
                writeMessage();
                break;
            }
            case CLOSE: {
                break; // ignore
            }
            default:
                throw new IllegalStateException("Invalid write state");
        }
    }

    public void shutdown() {
        open = false;
        closing();
        try {
            close();
        } catch (Exception ex) {
        }
    }

    abstract protected void closing();

    abstract protected void deliver(byte[] msg);

    abstract protected void logClose(String string, IOException e);

    protected void send(byte[] bytes) {
        try {
            writeGate.acquire();
        } catch (InterruptedException e) {
            return;
        }
        writeState = State.INITIAL;
        headerOut.clear();
        headerOut.putInt(0, MAGIC_NUMBER);
        headerOut.putInt(4, bytes.length);
        msgOut = ByteBuffer.wrap(bytes);
        writeHeader();
    }

    abstract protected void terminate();

    private void readHeader() {
        // Clear the buffer and read bytes 
        int numBytesRead;
        try {
            numBytesRead = channel.read(headerIn);
        } catch (ClosedChannelException e) {
            writeState = State.CLOSE;
            shutdown();
            return;
        } catch (IOException e) {
            logClose("Errror reading header", e);
            shutdown();
            return;
        }

        if (numBytesRead == -1) {
            readState = State.CLOSE;
            shutdown();
        } else if (!headerIn.hasRemaining()) {
            headerIn.flip();
            if (headerIn.getInt(0) != MAGIC_NUMBER) {
                readState = State.ERROR;
                logClose("incorrect magic number in header", null);
                shutdown();
                return;
            }
            int length = headerIn.getInt(4);
            byte[] msg = new byte[length];
            msgIn = ByteBuffer.wrap(msg);
            readState = State.MESSAGE;
            readMessage();
        } else {
            handler.selectForRead(this);
        }
    }

    private void readMessage() {
        int numBytesRead;
        try {
            numBytesRead = channel.read(msgIn);
        } catch (ClosedChannelException e) {
            writeState = State.CLOSE;
            shutdown();
            return;
        } catch (IOException e) {
            readState = State.ERROR;
            logClose("Error reading message body", e);
            terminate();
            return;
        }
        if (numBytesRead == -1) {
            readState = State.CLOSE;
            shutdown();
        } else if (msgIn.hasRemaining()) {
            handler.selectForRead(this);
        } else {
            byte[] msg = msgIn.array();
            msgIn = null;
            readState = State.INITIAL;
            deliver(msg);
            handler.selectForRead(this);
        }
    }

    private void writeHeader() {
        int bytesWritten;
        try {
            bytesWritten = channel.write(headerOut);
        } catch (ClosedChannelException e) {
            writeState = State.CLOSE;
            shutdown();
            return;
        } catch (IOException e) {
            writeState = State.ERROR;
            logClose("Unable to send message header", e);
            shutdown();
            return;
        }
        if (bytesWritten == -1) {
            writeState = State.CLOSE;
            shutdown();
        } else if (headerOut.hasRemaining()) {
            writeState = State.HEADER;
            handler.selectForWrite(this);
        } else {
            writeState = State.MESSAGE;
            writeMessage();
        }
    }

    private void writeMessage() {
        int bytesWritten;
        try {
            bytesWritten = channel.write(msgOut);
        } catch (ClosedChannelException e) {
            writeState = State.CLOSE;
            shutdown();
            return;
        } catch (IOException e) {
            writeState = State.ERROR;
            logClose("Unable to send message body", e);
            shutdown();
            return;
        }
        if (bytesWritten == -1) {
            writeState = State.CLOSE;
            shutdown();
        } else if (headerOut.hasRemaining()) {
            handler.selectForWrite(this);
        } else {
            writeState = State.INITIAL;
            msgOut = null;
            writeGate.release();
        }
    }
}