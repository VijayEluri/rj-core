/*******************************************************************************
 * Copyright (c) 2009-2010 Stephan Wahlbrink (www.walware.de/goto/opensource)
 * and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.rj.server;

import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * Interface to the R console / REPL
 */
public interface ConsoleEngine extends Remote {
	
	/**
	 * Returns the public server reference
	 * Usually you got this server by the public server reference
	 * 
	 * @return 
	 * @throws RemoteException
	 */
	Server getPublic() throws RemoteException;
	
	/**
	 * Tries to interrupt the current compution.
	 * 
	 * @throws RemoteException
	 */
	void interrupt() throws RemoteException;
	
	/**
	 * Disconnects the client from the server
	 * The client can reconnect using {@link #connect(long)}.
	 * 
	 * @throws RemoteException
	 */
	void disconnect() throws RemoteException;
	
	RjsComObject runMainLoop(RjsComObject com) throws RemoteException;
	RjsComObject runAsync(RjsComObject com) throws RemoteException;
	
	/**
	 * Tests if the connection to R is closed
	 * 
	 * @return <code>true</code> if engine is still valid, otherwise <code>false</code>
	 * @throws RemoteException
	 */
	boolean isClosed() throws RemoteException;
	
}
