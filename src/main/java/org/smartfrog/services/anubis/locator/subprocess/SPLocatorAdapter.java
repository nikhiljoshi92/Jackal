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
package org.smartfrog.services.anubis.locator.subprocess;

import java.rmi.Remote;
import java.rmi.RemoteException;

import org.smartfrog.services.anubis.locator.AnubisLocator;
import org.smartfrog.services.anubis.locator.ValueData;
import org.smartfrog.services.anubis.partition.util.Identity;

public interface SPLocatorAdapter extends Remote {
    public void registerSPLocator(AnubisLocator spLocator)
                                                          throws RemoteException,
                                                          DuplicateSPLocatorException;

    public void deregisterSPLocator(AnubisLocator spLocator)
                                                            throws RemoteException,
                                                            UnknownSPLocatorException;

    public SPProviderRegRet registerProvider(AnubisLocator subProcessLocator,
                                             String name, ValueData value)
                                                                          throws RemoteException,
                                                                          UnknownSPLocatorException;

    public void deregisterProvider(AnubisLocator subProcessLocator,
                                   String instance) throws RemoteException,
                                                   UnknownSPLocatorException;

    public void newProviderValue(AnubisLocator subProcessLocator,
                                 String instance, ValueData value, long time)
                                                                             throws RemoteException,
                                                                             UnknownSPLocatorException;

    public void registerListener(AnubisLocator subProcessLocator, String name,
                                 SPListener listener) throws RemoteException,
                                                     UnknownSPLocatorException;

    public void deregisterListener(AnubisLocator subProcessLocator,
                                   SPListener listener) throws RemoteException,
                                                       UnknownSPLocatorException;

    public void registerStability(AnubisLocator subProcessLocator,
                                  SPStability stability)
                                                        throws RemoteException,
                                                        UnknownSPLocatorException;

    public void deregisterStability(AnubisLocator subProcessLocator,
                                    SPStability stability)
                                                          throws RemoteException,
                                                          UnknownSPLocatorException;

    public void livenessPing(AnubisLocator subProcessLocator)
                                                             throws RemoteException,
                                                             UnknownSPLocatorException,
                                                             AdapterTerminatedException;

    public Identity getIdentity();
}