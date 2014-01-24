/*=============================================================================#
 # Copyright (c) 2008-2014 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the GNU Lesser General Public License
 # v2.1 or newer, which accompanies this distribution, and is available at
 # http://www.gnu.org/licenses/lgpl.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.rj.server.srvImpl;

import java.io.StreamCorruptedException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import de.walware.rj.RjException;
import de.walware.rj.RjInvalidConfigurationException;
import de.walware.rj.server.RjsComConfig;
import de.walware.rj.server.Server;
import de.walware.rj.server.jri.loader.JRIServerLoader;
import de.walware.rj.server.srvext.ServerAuthMethod;
import de.walware.rj.server.srvext.ServerRuntimePlugin;
import de.walware.rj.server.srvext.ServerUtil;


public class AbstractServerControl {
	
	
	public static final int EXIT_ARGS_PROBLEM = 130;
	public static final int EXIT_ARGS_MISSING = 131;
	public static final int EXIT_ARGS_INVALID = 132;
	public static final int EXIT_INIT_PROBLEM = 140;
	public static final int EXIT_INIT_LOGGING_ERROR = 141;
	public static final int EXIT_INIT_AUTHMETHOD_ERROR = 143;
	public static final int EXIT_INIT_RENGINE_ERROR = 145;
	public static final int EXIT_REGISTRY_PROBLEM = 150;
	public static final int EXIT_REGISTRY_INVALID_ADDRESS = 151;
	public static final int EXIT_REGISTRY_CONNECTING_ERROR = 151;
	public static final int EXIT_REGISTRY_SERVER_STILL_ACTIVE = 152;
	public static final int EXIT_REGISTRY_ALREADY_BOUND = 153;
	public static final int EXIT_REGISTRY_CLEAN_FAILED = 155;
	public static final int EXIT_REGISTRY_BIND_FAILED = 156;
	public static final int EXIT_START_RENGINE_ERROR = 161;
	
	protected static final Logger LOGGER = Logger.getLogger("de.walware.rj.server");
	
	protected static Map<String, String> cliGetArgs(final String[] args, final int first) {
		final Map<String, String> resolved = new HashMap<String, String>();
		for (int i = first; i < args.length; i++) {
			if (args[i].length() == 0 || args[i].charAt(0) != '-') {
				continue;
			}
			final int split = args[i].indexOf('=');
			if (split > 1) {
				resolved.put(args[i].substring(1, split), args[i].substring(split+1));
			}
			else if (split < 0) {
				resolved.put(args[i].substring(1), null);
			}
		}
		if (System.getProperty("de.walware.rj.verbose", "false").equals("true")) {
			resolved.put("verbose", "true");
		}
		return resolved;
	}
	
	public static void initVerbose() {
		if (VERBOSE) {
			return;
		}
		VERBOSE = true;
		VERBOSE_ON_ERROR = false;
		
		LOGGER.setLevel(Level.ALL); // default is Level.INFO
//		System.setProperty("java.rmi.server.logCalls", "true");
//		RemoteServer.setLog(System.err);
		ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
		
		Logger logger = LOGGER;
		SEARCH_CONSOLE: while (logger != null) {
			for (final Handler handler : logger.getHandlers()) {
				if (handler instanceof ConsoleHandler) {
					handler.setLevel(Level.ALL);
					break SEARCH_CONSOLE;
				}
			}
			if (!logger.getUseParentHandlers()) {
				break SEARCH_CONSOLE;
			}
			logger = logger.getParent();
		}
		
		final StringBuilder sb = new StringBuilder(256);
		
		LOGGER.log(Level.CONFIG, "verbose mode enabled.");
		
		sb.setLength(0);
		sb.append("java properties:");
		ServerUtil.prettyPrint(System.getProperties(), sb);
		LOGGER.log(Level.CONFIG, sb.toString());
		
		sb.setLength(0);
		sb.append("env variables:");
		ServerUtil.prettyPrint(System.getenv(), sb);
		LOGGER.log(Level.CONFIG, sb.toString());
	}
	
	private static boolean VERBOSE;
	private static boolean VERBOSE_ON_ERROR;
	
	public static void exit(final int status) {
		try {
			if (status != 0 && VERBOSE_ON_ERROR) {
				initVerbose();
			}
			System.err.flush();
			System.out.flush();
		}
		finally {
			System.exit(status);
		}
	}
	
	public Remote exportObject(final Remote obj) throws RemoteException {
		RMIClientSocketFactory csf = RjsComConfig.getRMIServerClientSocketFactory();
		RMIServerSocketFactory ssf = null;
		if (this.rmiAddress.isSSL()) {
			csf = new SslRMIClientSocketFactory();
			ssf = new SslRMIServerSocketFactory(null, null, true);
		}
		return UnicastRemoteObject.exportObject(obj, 0, csf, ssf);
	}
	
	
	protected final String logPrefix;
	
	private final RMIAddress rmiAddress;
	
	protected final Map<String, String> args;
	
	protected Server mainServer;
	private boolean isPublished;
	
	
	protected AbstractServerControl(final String name, final Map<String, String> args) {
		final int lastSegment = name.lastIndexOf('/');
		this.logPrefix = "[Control:"+((lastSegment >= 0) ? name.substring(lastSegment+1) : name)+"]";
		this.args = args;
		
		{	RMIAddress address = null;
			Exception error = null;
			try {
				address = new RMIAddress(name);
			}
			catch (final MalformedURLException e) {
				error = e;
			}
			catch (final UnknownHostException e) {
				error = e;
			}
			if (address == null) {
				final LogRecord record = new LogRecord(Level.SEVERE,
						"{0} the server address ''{1}'' is invalid.");
				record.setParameters(new Object[] { this.logPrefix, name });
				record.setThrown(error);
				LOGGER.log(record);
				
				exit(EXIT_REGISTRY_INVALID_ADDRESS);
			}
			this.rmiAddress = address;
		}
		
		if (args != null) {
			if (args.containsKey("verbose")) {
				initVerbose();
			}
			if (args.containsKey("embedded")) {
				if (System.getProperty("de.walware.rj.rmi.disableSocketFactory") == null) {
					System.setProperty("de.walware.rj.rmi.disableSocketFactory", "true");
				}
				if (!VERBOSE) {
					VERBOSE_ON_ERROR = true;
				}
			}
		}
	}
	
	
	public boolean initREngine(final DefaultServerImpl server) {
		InternalEngine engine = null;
		try {
			engine = new JRIServerLoader().loadServer(this, this.args, server, new ServerRuntimePlugin() {
				
				@Override
				public String getSymbolicName() {
					return "rmi";
				}
				
				@Override
				public void rjIdle() throws Exception {
				}
				
				@Override
				public void rjStop(final int state) throws Exception {
					if (state == 0) {
						try {
							Thread.sleep(1000);
						}
						catch (final InterruptedException e) {
						}
					}
					checkCleanup();
				};
				
			});
			server.setEngine(engine);
			return true;
		}
		catch (final Throwable e) {
			final LogRecord record = new LogRecord(Level.SEVERE,
					"{0} init JRI/Rengine failed.");
			record.setParameters(new Object[] { this.logPrefix });
			record.setThrown(e);
			LOGGER.log(record);
			
			checkCleanup();
			return false;
		}
	}
	
	protected Registry getRegistry() throws RemoteException {
		RMIClientSocketFactory csf = null;
		if (this.rmiAddress.isSSL()) {
			csf = new SslRMIClientSocketFactory();
		}
		return LocateRegistry.getRegistry(this.rmiAddress.getHost(), this.rmiAddress.getPortNum(), csf);
	}
	
	public String getName() {
		return this.rmiAddress.getName();
	}
	
	protected void publishServer(final Server server) {
		try {
			System.setSecurityManager(new SecurityManager());
			final Registry registry = getRegistry();
			Server stub = (Server) exportObject(server);
			this.mainServer = server;
			try {
				registry.bind(getName(), stub);
			}
			catch (final AlreadyBoundException boundException) {
				if (unbindDead() == 0) {
					registry.bind(getName(), stub);
				}
				else {
					throw boundException;
				}
			}
			catch (final RemoteException remoteException) {
				if (remoteException.getCause() instanceof UnmarshalException
						&& remoteException.getCause().getCause() instanceof StreamCorruptedException
						&& RjsComConfig.getRMIServerClientSocketFactory() != null) {
					stub = null;
					try {
						UnicastRemoteObject.unexportObject(server, true);
						stub = (Server) UnicastRemoteObject.exportObject(server, 0, null, null);
					}
					catch (final Exception testException) {}
					if (stub != null) {
						final LogRecord record = new LogRecord(Level.SEVERE,
								"{0} caught StreamCorruptedException \nretrying without socket factory to reveal other potential problems.");
						record.setParameters(new Object[] { this.logPrefix });
						record.setThrown(remoteException);
						LOGGER.log(record);
						registry.bind(getName(), stub);
						registry.unbind(getName());
						throw new RjException("No error without socket factory, use the Java property 'de.walware.rj.rmi.disableSocketFactory' to disable the factory.");
					}
				}
				throw remoteException;
			}
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					checkCleanup();
				}
			});
			this.isPublished = true;
			LOGGER.log(Level.INFO, "{0} server is added to registry - ready.", this.logPrefix);
			
			return;
		}
		catch (final Exception e) {
			final LogRecord record = new LogRecord(Level.SEVERE,
					"{0} init server failed.");
			record.setParameters(new Object[] { this.logPrefix });
			record.setThrown(e);
			LOGGER.log(record);
			
			if (e instanceof AlreadyBoundException) {
				exit(EXIT_REGISTRY_ALREADY_BOUND);
			}
			
			checkCleanup();
			exit(EXIT_REGISTRY_BIND_FAILED);
		}
	}
	
	/**
	 * @return <code>true</code> if it was removed, otherwise <code>false</code>
	 */
	protected int unbindDead() {
		return 1;
	}
	
	public void checkCleanup() {
		if (this.mainServer == null) {
			return;
		}
		LOGGER.log(Level.INFO, "{0} cleaning up server resources...", this.logPrefix);
		try {
			final Registry registry = getRegistry();
			registry.unbind(getName());
		}
		catch (final NotBoundException e) {
			// ok
		}
		catch (final Exception e) {
			final LogRecord record = new LogRecord(this.isPublished ? Level.SEVERE : Level.INFO,
					"{0} cleaning up server resources failed.");
			record.setParameters(new Object[] { this.logPrefix });
			record.setThrown(e);
			LOGGER.log(record);
		}
		try {
			UnicastRemoteObject.unexportObject(this.mainServer, true);
		}
		catch (final NoSuchObjectException e) {
			// ok
		}
		this.mainServer = null;
		System.gc();
	}
	
	public ServerAuthMethod createServerAuth(final String config) throws RjException {
		// auth
		final String authType;
		final String authConfig;
		try {
			final String[] auth = ServerUtil.getArgSubValue(config);
			if (auth[0].length() == 0) {
				throw new RjInvalidConfigurationException("Missing 'auth' configuration");
			}
			else if (auth[0].equals("none")) {
				authType = "de.walware.rj.server.srvstdext.NoAuthMethod";
			}
			else if (auth[0].equals("name-pass")) {
				authType = "de.walware.rj.server.srvstdext.SimpleNamePassAuthMethod";
			}
			else if (auth[0].equals("fx")) {
				authType = "de.walware.rj.server.srvstdext.FxAuthMethod";
			}
			else if (auth[0].equals("local-shaj")) {
				authType = "de.walware.rj.server.authShaj.LocalShajAuthMethod";
			}
			else {
				authType = auth[0];
			}
			authConfig = auth[1];
		}
		catch (final Exception e) {
			final LogRecord record = new LogRecord(Level.SEVERE,
					"{0} init authentication method failed.");
			record.setParameters(new Object[] { this.logPrefix });
			record.setThrown(e);
			LOGGER.log(record);
			throw new RjInvalidConfigurationException("Init authentication method failed.", e);
		}
		try {
			final Class<ServerAuthMethod> authClazz = (Class<ServerAuthMethod>) Class.forName(authType);
			final ServerAuthMethod authMethod = authClazz.newInstance();
			authMethod.init(authConfig);
			return authMethod;
		}
		catch (final Exception e) {
			final LogRecord record = new LogRecord(Level.SEVERE,
					"{0} init authentication method '{1}' failed.");
			record.setParameters(new Object[] { this.logPrefix, authType });
			record.setThrown(e);
			LOGGER.log(record);
			throw new RjException(MessageFormat.format("Init authentication method failed '{0}'.", authType), e);
		}
	}
	
}
