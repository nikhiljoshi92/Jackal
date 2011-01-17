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
package org.smartfrog.services.anubis.locator.registers;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.smartfrog.services.anubis.locator.util.BlockingQueue;
import org.smartfrog.services.anubis.partition.views.View;

abstract public class StabilityQueue {
    private class Notification {
        int leader;
        View view;

        public Notification(View view, int leader) {
            this.view = view;
            this.leader = leader;
        }
    }

    private class RequestServer extends Thread {
        private boolean running = false;

        public RequestServer() {
            super("Anubis: Locator Stability Queue Server");
            setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    log.log(Level.WARNING, "Uncaught exception", e);
                }
            });
        }

        @Override
        public void run() {
            running = true;
            while (running) {
                try {
                    Notification notification = (Notification) requests.get();
                    if (notification != null) {
                        doit(notification.view, notification.leader);
                    }
                } catch (Throwable e) {
                    log.log(Level.WARNING, "Exception delivering notification",
                            e);
                }
            }
        }

        public void terminate() {
            running = false;
        }
    }

    private static final Logger log = Logger.getLogger(StabilityQueue.class.getCanonicalName());

    private BlockingQueue requests = new BlockingQueue();
    private RequestServer server = new RequestServer();

    public StabilityQueue() {
    }

    abstract public void doit(View view, int leader);

    public void put(View view, int leader) {
        requests.put(new Notification(view, leader));
    }

    public void start() {
        server.start();
    }

    public void terminate() {
        server.terminate();
        requests.deactivate();
    }
}