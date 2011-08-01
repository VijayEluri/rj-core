/*******************************************************************************
 * Copyright (c) 2008-2011 Stephan Wahlbrink (www.walware.de/goto/opensource)
 * and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.rj.server.jri;

import static de.walware.rj.data.RObjectFactory.F_ONLY_STRUCT;
import static de.walware.rj.server.RjsComObject.V_ERROR;
import static de.walware.rj.server.RjsComObject.V_FALSE;
import static de.walware.rj.server.RjsComObject.V_OK;
import static de.walware.rj.server.RjsComObject.V_TRUE;
import static de.walware.rj.server.Server.S_CONNECTED;
import static de.walware.rj.server.Server.S_DISCONNECTED;
import static de.walware.rj.server.Server.S_NOT_STARTED;
import static de.walware.rj.server.Server.S_STOPPED;

import java.lang.reflect.Field;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rosuda.JRI.RConfig;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.RMainLoopCallbacks;
import org.rosuda.JRI.Rengine;

import de.walware.rj.RjException;
import de.walware.rj.RjInitFailedException;
import de.walware.rj.data.RCharacterStore;
import de.walware.rj.data.RComplexStore;
import de.walware.rj.data.RDataFrame;
import de.walware.rj.data.RDataUtil;
import de.walware.rj.data.RFactorStore;
import de.walware.rj.data.RIntegerStore;
import de.walware.rj.data.RLanguage;
import de.walware.rj.data.RList;
import de.walware.rj.data.RLogicalStore;
import de.walware.rj.data.RNumericStore;
import de.walware.rj.data.RObject;
import de.walware.rj.data.RObjectFactory;
import de.walware.rj.data.RRawStore;
import de.walware.rj.data.RReference;
import de.walware.rj.data.RS4Object;
import de.walware.rj.data.RStore;
import de.walware.rj.data.defaultImpl.RCharacterDataImpl;
import de.walware.rj.data.defaultImpl.RFactorDataImpl;
import de.walware.rj.data.defaultImpl.RFactorDataStruct;
import de.walware.rj.data.defaultImpl.RFunctionImpl;
import de.walware.rj.data.defaultImpl.RMissing;
import de.walware.rj.data.defaultImpl.RNull;
import de.walware.rj.data.defaultImpl.RObjectFactoryImpl;
import de.walware.rj.data.defaultImpl.ROtherImpl;
import de.walware.rj.data.defaultImpl.RPromise;
import de.walware.rj.data.defaultImpl.RReferenceImpl;
import de.walware.rj.data.defaultImpl.RS4ObjectImpl;
import de.walware.rj.data.defaultImpl.SimpleRListImpl;
import de.walware.rj.server.ConsoleEngine;
import de.walware.rj.server.ConsoleMessageCmdItem;
import de.walware.rj.server.ConsoleReadCmdItem;
import de.walware.rj.server.ConsoleWriteErrCmdItem;
import de.walware.rj.server.ConsoleWriteOutCmdItem;
import de.walware.rj.server.CtrlCmdItem;
import de.walware.rj.server.DataCmdItem;
import de.walware.rj.server.ExtUICmdItem;
import de.walware.rj.server.GraOpCmdItem;
import de.walware.rj.server.MainCmdC2SList;
import de.walware.rj.server.MainCmdItem;
import de.walware.rj.server.MainCmdS2CList;
import de.walware.rj.server.RJ;
import de.walware.rj.server.RjsComConfig;
import de.walware.rj.server.RjsComObject;
import de.walware.rj.server.RjsException;
import de.walware.rj.server.RjsStatus;
import de.walware.rj.server.Server;
import de.walware.rj.server.gd.Coord;
import de.walware.rj.server.gd.GraOp;
import de.walware.rj.server.srvImpl.AbstractServerControl;
import de.walware.rj.server.srvImpl.ConsoleEngineImpl;
import de.walware.rj.server.srvImpl.DefaultServerImpl;
import de.walware.rj.server.srvImpl.InternalEngine;
import de.walware.rj.server.srvImpl.RJClassLoader;
import de.walware.rj.server.srvext.Client;
import de.walware.rj.server.srvext.ExtServer;
import de.walware.rj.server.srvext.RjsGraphic;
import de.walware.rj.server.srvext.ServerRuntimePlugin;


/**
 * Remove server based on
 */
public final class JRIServer extends RJ
		implements InternalEngine, RMainLoopCallbacks, ExtServer {
	
	
	private static final int ENGINE_NOT_STARTED = 0;
	private static final int ENGINE_RUN_IN_R = 1;
	private static final int ENGINE_WAIT_FOR_CLIENT = 2;
	private static final int ENGINE_STOPPED = 4;
	
	private static final int CLIENT_NONE = 0;
	private static final int CLIENT_OK = 1;
	private static final int CLIENT_OK_WAIT = 2;
	private static final int CLIENT_CANCEL = 3;
	
	private static final long STALE_SPAN = 5L * 60L * 1000000000L;
	
	private static final int KILO = 1024;
	private static final int MEGA = 1048576;
	private static final int GIGA = 1073741824;
	
	private static final Logger LOGGER = Logger.getLogger("de.walware.rj.server.jri");
	
	private static final int STDOUT_BUFFER_SIZE = 0x1FFF;
	
	private static final long REQUIRED_JRI_API = 0x010a;
	
	private static final byte EVAL_MODE_DEFAULT = 0;
	private static final byte EVAL_MODE_FORCE = 1;
	private static final byte EVAL_MODE_DATASLOT = 2;
	
	private static final int CODE_CTRL_COMMON = 0x2000;
	private static final int CODE_CTRL_REQUEST_CANCEL = 0x2010;
	private static final int CODE_CTRL_REQUEST_HOT_MODE = 0x2020;
	
	private static final int CODE_DATA_COMMON = 0x1000;
	private static final int CODE_DATA_EVAL_DATA = 0x1010;
	private static final int CODE_DATA_RESOLVE_DATA = 0x1020;
	private static final int CODE_DATA_ASSIGN_DATA = 0x1030;
	
	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final RObject[] EMPTY_ROBJECT_ARRAY = new RObject[0];
	private static final String[] DATA_NAME_ARRAY = new String[] { ".Data" };
	
	private static long s2long(final String s, final long defaultValue) {
		if (s != null && s.length() > 0) {
			final int multi;
			switch (s.charAt(s.length()-1)) {
			case 'G':
				multi = GIGA;
				break;
			case 'M':
				multi = MEGA;
				break;
			case 'K':
				multi = KILO;
				break;
			case 'k':
				multi = 1000;
				break;
			default:
				multi = 1;
				break;
			}
			try {
				if (multi != 1) {
					return Long.parseLong(s.substring(0, s.length()-1)) * multi;
				}
				else {
					return Long.parseLong(s);
				}
			}
			catch (final NumberFormatException e) {}
		}
		return defaultValue;
	}
	
	
	private class InitCallbacks implements RMainLoopCallbacks {
		public String rReadConsole(final Rengine re, final String prompt, final int addToHistory) {
			initEngine(re);
			return JRIServer.this.rReadConsole(re, prompt, addToHistory);
		}
		public void rWriteConsole(final Rengine re, final String text, final int oType) {
			initEngine(re);
			JRIServer.this.rWriteConsole(re, text, oType);
		}
		public void rFlushConsole(final Rengine re) {
			initEngine(re);
			JRIServer.this.rFlushConsole(re);
		}
		public void rBusy(final Rengine re, final int which) {
			initEngine(re);
			JRIServer.this.rBusy(re, which);
		}
		public void rShowMessage(final Rengine re, final String message) {
			initEngine(re);
			JRIServer.this.rShowMessage(re, message);
		}
		public String rChooseFile(final Rengine re, final int newFile) {
			initEngine(re);
			return JRIServer.this.rChooseFile(re, newFile);
		}
		public void rLoadHistory(final Rengine re, final String filename) {
			initEngine(re);
			JRIServer.this.rLoadHistory(re, filename);
		}
		public void rSaveHistory(final Rengine re, final String filename) {
			initEngine(re);
			JRIServer.this.rSaveHistory(re, filename);
		}
		public long rExecJCommand(final Rengine re, final String commandId, final long argsExpr, final int options) {
			initEngine(re);
			return JRIServer.this.rExecJCommand(re, commandId, argsExpr, options);
		}
		public void rProcessJEvents(final Rengine re) {
			JRIServer.this.mainExchangeLock.lock();
			try {
				if (JRIServer.this.hotModeRequested) {
					JRIServer.this.hotModeDelayed = true;
				}
			}
			finally {
				JRIServer.this.mainExchangeLock.unlock();
			}
		}
	}
	
	private class HotLoopCallbacks implements RMainLoopCallbacks {
		public String rReadConsole(final Rengine re, final String prompt, final int addToHistory) {
			if (prompt.startsWith("Browse")) {
				return "c\n";
			}
			return "\n";
		}
		public void rWriteConsole(final Rengine re, final String text, final int oType) {
			LOGGER.log(Level.WARNING, "HotMode - Console Output:\n" + text);
		}
		public void rFlushConsole(final Rengine re) {
			JRIServer.this.rFlushConsole(re);
		}
		public void rBusy(final Rengine re, final int which) {
			JRIServer.this.rBusy(re, which);
		}
		public void rShowMessage(final Rengine re, final String message) {
			LOGGER.log(Level.WARNING, "HotMode - Message:\n" + message);
		}
		public String rChooseFile(final Rengine re, final int newFile) {
			return null;
		}
		public void rLoadHistory(final Rengine re, final String filename) {
		}
		public void rSaveHistory(final Rengine re, final String filename) {
		}
		public long rExecJCommand(final Rengine re, final String commandId, final long argsExpr, final int options) {
			return 0;
		}
		public void rProcessJEvents(final Rengine re) {
		}
	}
	
	
	private String name;
	
	private Server publicServer;
	private RJClassLoader rClassLoader;
	private Rengine rEngine;
	private List<String> rArgs;
	private RConfig rConfig;
	private long rMemSize;
	
	private final ReentrantLock mainExchangeLock = new ReentrantLock();
	private final Condition mainExchangeClient = this.mainExchangeLock.newCondition();
	private final Condition mainExchangeR = this.mainExchangeLock.newCondition();
	private final ReentrantLock mainInterruptLock = new ReentrantLock();
	private int mainLoopState;
	private boolean mainLoopBusyAtServer = false;
	private boolean mainLoopBusyAtClient = true;
	private int mainLoopClient0State;
	private int mainLoopClientListen;
	private int mainLoopServerStack;
	private final char[] mainLoopStdOutBuffer = new char[STDOUT_BUFFER_SIZE];
	private String mainLoopStdOutSingle;
	private int mainLoopStdOutSize;
	private final MainCmdItem[] mainLoopS2CNextCommandsFirst = new MainCmdItem[2];
	private final MainCmdItem[] mainLoopS2CNextCommandsLast = new MainCmdItem[2];
	private final MainCmdS2CList[] mainLoopS2CLastCommands = new MainCmdS2CList[] { new MainCmdS2CList(), new MainCmdS2CList() };
	private final List<MainCmdItem> mainLoopS2CRequest = new ArrayList<MainCmdItem>();
	private MainCmdItem mainLoopC2SCommandFirst;
	private int mainLoopS2CAnswerFail;
	
	private boolean safeMode;
	
	private boolean hotModeRequested;
	private boolean hotModeDelayed;
	private boolean hotMode;
	private final RMainLoopCallbacks hotModeCallbacks = new HotLoopCallbacks();
	
	private int serverState;
	
	private final ReentrantReadWriteLock[] clientLocks = new ReentrantReadWriteLock[] {
			new ReentrantReadWriteLock(), new ReentrantReadWriteLock() };
	private Client client0;
	private ConsoleEngine client0Engine;
	private ConsoleEngine client0ExpRef;
	private ConsoleEngine client0PrevExpRef;
	private volatile long client0LastPing;
	
	private final Object pluginsLock = new Object();
	private ServerRuntimePlugin[] pluginsList = new ServerRuntimePlugin[0];
	
	private int rniDepth;
	private boolean rniEvalTempAssigned;
	private int rniMaxDepth;
	private boolean rniInterrupted;
	private int rniProtectedCounter;
	private int rniListsMaxLength = 10000;
	private int rniEnvsMaxLength = 10000;
	
	private long rniP_NULL;
	private long rniP_Unbound;
	private long rniP_MissingArg;
	private long rniP_BaseEnv;
	private long rniP_AutoloadEnv;
	private long rniP_RJTempEnv;
	
	private long rniP_functionSymbol;
	private long rniP_AssignSymbol;
	private long rniP_xSymbol;
	private long rniP_zSymbol;
	private long rniP_envSymbol;
	private long rniP_nameSymbol;
	private long rniP_namesSymbol;
	private long rniP_dimNamesSymbol;
	private long rniP_rowNamesSymbol;
	private long rniP_levelsSymbol;
	private long rniP_realSymbol;
	private long rniP_imaginarySymbol;
	private long rniP_newSymbol;
	private long rniP_ClassSymbol;
	private long rniP_classSymbol;
	private long rniP_exprSymbol;
	private long rniP_errorSymbol;
	
	private long rniP_factorClassString;
	private long rniP_orderedClassString;
	private long rniP_dataframeClassString;
	private long rniP_complexFun;
	private long rniP_envNameFun;
	private long rniP_headerFun;
	private long rniP_deparseLineXCall;
	private long rniP_slotNamesFun;
	private long rniP_ReFun;
	private long rniP_ImFun;
	private long rniP_tryCatchFun;
	
	private long rniP_evalTryCatch_errorExpr;
	private long rniP_evalTemp_classExpr;
	private long rniP_evalTemp_rmExpr;
	private long rniP_evalDummyExpr;
	
	private RObjectFactory rObjectFactory;
	
	private JRIServerGraphics graphics;
	
	private Map<String, String> platformDataCommands;
	private final Map<String, Object> platformDataValues = new HashMap<String, Object>();
	
	
	public JRIServer() {
		this.rConfig = new RConfig();
		// default 16M, overwritten by arg --max-cssize, if set
		this.rConfig.MainCStack_Size = s2long(
				System.getProperty("de.walware.rj.rMainCStack_Size"), 16 * MEGA );
		// default true
		this.rConfig.MainCStack_SetLimit = !"false".equalsIgnoreCase(
				System.getProperty("de.walware.rj.rMainCStack_SetLimit") );
		
		this.mainLoopState = ENGINE_NOT_STARTED;
		this.mainLoopClient0State = CLIENT_NONE;
		
		this.serverState = S_NOT_STARTED;
	}
	
	
	public int[] getVersion() {
		return new int[] { 1, 0, 0 };
	}
	
	public void init(final String name, final Server publicServer, final RJClassLoader loader) throws Exception {
		if (loader == null) {
			throw new NullPointerException("loader");
		}
		this.name = name;
		this.publicServer = publicServer;
		this.rClassLoader = loader;
		
		this.platformDataCommands = new HashMap<String, String>();
		this.platformDataCommands.put("os.type", ".Platform$OS.type");
		this.platformDataCommands.put("file.sep", ".Platform$file.sep");
		this.platformDataCommands.put("path.sep", ".Platform$path.sep");
		this.platformDataCommands.put("version.string", "paste(R.version$major, R.version$minor, sep=\".\")");
	}
	
	public void addPlugin(final ServerRuntimePlugin plugin) {
		if (plugin == null) {
			throw new IllegalArgumentException();
		}
		synchronized (this.pluginsLock) {
			final int oldSize = this.pluginsList.length;
			final ServerRuntimePlugin[] newList = new ServerRuntimePlugin[oldSize + 1];
			System.arraycopy(this.pluginsList, 0, newList, 0, oldSize);
			newList[oldSize] = plugin;
			this.pluginsList= newList;
		}
	}
	
	public void removePlugin(final ServerRuntimePlugin plugin) {
		if (plugin == null) {
			return;
		}
		synchronized (this.pluginsLock) {
			final int oldSize = this.pluginsList.length;
			for (int i = 0; i < oldSize; i++) {
				if (this.pluginsList[i] == plugin) {
					final ServerRuntimePlugin[] newList = new ServerRuntimePlugin[oldSize - 1];
					System.arraycopy(this.pluginsList, 0, newList, 0, i);
					System.arraycopy(this.pluginsList, i + 1, newList, i, oldSize - i - 1);
					this.pluginsList = newList;
					return;
				}
			}
		}
	}
	
	private void handlePluginError(final ServerRuntimePlugin plugin, final Throwable error) {
		// log and disable
		LOGGER.log(Level.SEVERE, "[Plugins] An error occurred in plug-in '"+plugin.getSymbolicName()+"', plug-in will be disabled.", error);
		removePlugin(plugin);
		try {
			plugin.rjStop(V_ERROR);
		}
		catch (final Throwable stopError) {
			LOGGER.log(Level.WARNING, "[Plugins] An error occurred when trying to disable plug-in '"+plugin.getSymbolicName()+"'.", error);
		}
	}
	
	
	private void connectClient0(final Client client,
			final ConsoleEngine consoleEngine, final ConsoleEngine export) {
		disconnectClient0();
		
		this.client0 = client;
		this.client0Engine = consoleEngine;
		this.client0ExpRef = export;
		DefaultServerImpl.addClient(export);
		this.client0LastPing = System.nanoTime();
		this.serverState = S_CONNECTED;
	}
	
	private void disconnectClient0() {
		if (this.serverState >= S_CONNECTED && this.serverState < S_STOPPED) {
			this.serverState = S_DISCONNECTED;
		}
		if (this.client0PrevExpRef != null) {
			try {
				UnicastRemoteObject.unexportObject(this.client0PrevExpRef, true);
			}
			catch (final Exception e) {}
			this.client0PrevExpRef = null;
		}
		if (this.client0 != null) {
			final Client client = this.client0;
			DefaultServerImpl.removeClient(this.client0ExpRef);
			this.client0 = null;
			this.client0Engine = null;
			this.client0PrevExpRef = this.client0ExpRef;
			this.client0ExpRef = null;
			
			if (this.hotModeRequested) {
				this.hotModeRequested = false;
			}
			internalClearClient(client);
		}
	}
	
	
	private void checkClient(final Client client) throws RemoteException {
//		final String expectedClient = this.currentMainClientId;
//		final String remoteClient;
//		try {
//			remoteClient = RemoteServer.getClientHost();
//		}
//		catch (final ServerNotActiveException e) {
//			throw new IllegalStateException(e);
//		}
		if (client.slot == 0 && this.client0 != client
//				|| expectedClient == null 
//				|| !expectedClient.equals(remoteClient)
				) {
			throw new ConnectException("Not connected.");
		}
	}
	
	public Client getCurrentClient() {
		return this.client0;
	}
	
	public int getState() {
		final int state = this.serverState;
		if (state == Server.S_CONNECTED
				&& (System.nanoTime() - this.client0LastPing) > STALE_SPAN) {
			return Server.S_CONNECTED_STALE;
		}
		return state;
	}
	
	public synchronized ConsoleEngine start(final Client client, final Map<String, ? extends Object> properties) throws RemoteException {
		assert (client.slot == 0);
		this.clientLocks[client.slot].writeLock().lock();
		try {
			if (this.mainLoopState != ENGINE_NOT_STARTED) {
				throw new IllegalStateException("R engine is already started.");
			}
			
			final ConsoleEngineImpl consoleEngine = new ConsoleEngineImpl(this.publicServer, this, client);
			final ConsoleEngine export = (ConsoleEngine) UnicastRemoteObject.exportObject(consoleEngine, 0);
			
			final ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(this.rClassLoader);
				
				if (Rengine.getVersion() < REQUIRED_JRI_API) {
					final String message = "Unsupported JRI version (API found: 0x" + Long.toHexString(Rengine.getVersion()) + ", required: 0x" + Long.toHexString(REQUIRED_JRI_API) + ").";
					LOGGER.log(Level.SEVERE, message);
					internalRStopped();
					throw new RjInitFailedException(message);
				}
				
				final String[] args = checkArgs((String[]) properties.get("args"));
				
				this.mainLoopState = ENGINE_RUN_IN_R;
				this.hotMode = true;
				final Rengine re = new Rengine(args, this.rConfig, true, new InitCallbacks());
				
				while (this.rEngine != re) {
					Thread.sleep(100);
				}
				if (!re.waitForR()) {
					internalRStopped();
					throw new IllegalThreadStateException("R thread not started");
				}
				
				this.mainExchangeLock.lock();
				try {
					this.mainLoopS2CAnswerFail = 0;
					this.mainLoopClient0State = CLIENT_OK;
				}
				finally {
					this.mainExchangeLock.unlock();
				}
				
				setProperties(client.slot, properties, true);
				
				LOGGER.log(Level.INFO, "R engine started successfully. New Client-State: 'Connected'.");
				
				connectClient0(client, consoleEngine, export);
				return export;
			}
			catch (final Throwable e) {
				this.serverState = S_STOPPED;
				final String message = "Could not start the R engine";
				LOGGER.log(Level.SEVERE, message, e);
				if (export != null) {
					UnicastRemoteObject.unexportObject(export, true);
				}
				throw new RemoteException(message, e);
			}
			finally {
				Thread.currentThread().setContextClassLoader(oldLoader);
			}
		}
		finally {
			this.clientLocks[client.slot].writeLock().unlock();
		}
	}
	
	public void setProperties(final Client client, final Map<String, ? extends Object> properties) throws RemoteException {
		this.clientLocks[client.slot].readLock().lock();
		try {
			checkClient(client);
			setProperties(client.slot, properties, properties.containsKey("rj.com.init"));
		}
		finally {
			this.clientLocks[client.slot].readLock().unlock();
		}
	}
	
	private void setProperties(final byte slot, final Map<String, ? extends Object> properties, final boolean init) {
		{	final Object max = properties.get(RjsComConfig.RJ_DATA_STRUCTS_LISTS_MAX_LENGTH_PROPERTY_ID);
			if (max instanceof Integer) {
				this.rniListsMaxLength = ((Integer) max).intValue();
			}
		}
		{	final Object max = properties.get(RjsComConfig.RJ_DATA_STRUCTS_ENVS_MAX_LENGTH_PROPERTY_ID);
			if (max instanceof Integer) {
				this.rniEnvsMaxLength = ((Integer) max).intValue();
			}
		}
		if (init) {
			final Object id = properties.get(RjsComConfig.RJ_COM_S2C_ID_PROPERTY_ID);
			if (id instanceof Integer) {
				this.mainLoopS2CLastCommands[slot].setId(((Integer) id).intValue());
			}
			else {
				this.mainLoopS2CLastCommands[slot].setId(0);
			}
		}
		else {
			properties.remove(RjsComConfig.RJ_COM_S2C_ID_PROPERTY_ID);
		}
		super.setClientProperties(slot, properties);
	}
	
	private void initEngine(final Rengine re) {
		this.rEngine = re;
		this.rEngine.setContextClassLoader(this.rClassLoader);
		this.rObjectFactory = new JRIObjectFactory();
		RjsComConfig.setDefaultRObjectFactory(this.rObjectFactory);
		
		this.rEngine.addMainLoopCallbacks(this.hotModeCallbacks);
		final int savedProtectedCounter = this.rniProtectedCounter;
		try {
			this.rniP_NULL = this.rEngine.rniSpecialObject(Rengine.SO_NilValue);
			this.rniP_Unbound = this.rEngine.rniSpecialObject(Rengine.SO_UnboundValue);
			this.rniP_MissingArg = this.rEngine.rniSpecialObject(Rengine.SO_MissingArg);
			this.rniP_BaseEnv = this.rEngine.rniSpecialObject(Rengine.SO_BaseEnv);
			
			this.rniP_AutoloadEnv = this.rEngine.rniEval(this.rEngine.rniInstallSymbol(".AutoloadEnv"),
					this.rniP_BaseEnv );
			if (this.rniP_AutoloadEnv != 0 && this.rEngine.rniExpType(this.rniP_AutoloadEnv) != REXP.ENVSXP) {
				this.rniP_AutoloadEnv = 0;
			}
			
			this.rniP_functionSymbol = this.rEngine.rniInstallSymbol("function");
			this.rniP_AssignSymbol = this.rEngine.rniInstallSymbol("<-");
			this.rniP_xSymbol = this.rEngine.rniInstallSymbol("x");
			this.rniP_zSymbol = this.rEngine.rniInstallSymbol("z");
			this.rniP_envSymbol = this.rEngine.rniInstallSymbol("env");
			this.rniP_nameSymbol = this.rEngine.rniInstallSymbol("name");
			this.rniP_namesSymbol = this.rEngine.rniInstallSymbol("names");
			this.rniP_dimNamesSymbol = this.rEngine.rniInstallSymbol("dimnames");
			this.rniP_rowNamesSymbol = this.rEngine.rniInstallSymbol("row.names");
			this.rniP_levelsSymbol = this.rEngine.rniInstallSymbol("levels");
			this.rniP_classSymbol = this.rEngine.rniInstallSymbol("class");
			this.rniP_realSymbol = this.rEngine.rniInstallSymbol("real");
			this.rniP_imaginarySymbol = this.rEngine.rniInstallSymbol("imaginary");
			this.rniP_newSymbol = this.rEngine.rniInstallSymbol("new");
			this.rniP_ClassSymbol = this.rEngine.rniInstallSymbol("Class");
			this.rniP_exprSymbol = this.rEngine.rniInstallSymbol("expr");
			this.rniP_errorSymbol = this.rEngine.rniInstallSymbol("error");
			
			this.rniP_RJTempEnv = this.rEngine.rniEval(this.rEngine.rniCons(
					this.rEngine.rniInstallSymbol("new.env"), this.rniP_NULL, 0, true ),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_RJTempEnv);
			this.rniP_orderedClassString = this.rEngine.rniPutStringArray(
					new String[] { "ordered", "factor" } );
			this.rEngine.rniPreserve(this.rniP_orderedClassString);
			this.rniP_factorClassString = this.rEngine.rniPutString("factor");
			this.rEngine.rniPreserve(this.rniP_factorClassString);
			this.rniP_dataframeClassString = this.rEngine.rniPutString("data.frame");
			this.rEngine.rniPreserve(this.rniP_dataframeClassString);
			
			this.rniP_complexFun = this.rEngine.rniEval(this.rEngine.rniInstallSymbol("complex"),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_complexFun);
			this.rniP_envNameFun = this.rEngine.rniEval(this.rEngine.rniInstallSymbol("environmentName"),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_envNameFun);
			this.rniP_ReFun = this.rEngine.rniEval(this.rEngine.rniInstallSymbol("Re"),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_ReFun);
			this.rniP_ImFun = this.rEngine.rniEval(this.rEngine.rniInstallSymbol("Im"),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_ImFun);
			this.rniP_tryCatchFun = this.rEngine.rniEval(this.rEngine.rniInstallSymbol("tryCatch"),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_tryCatchFun);
			
			{	final long pasteFun = rniProtect(this.rEngine.rniEval(
						this.rEngine.rniInstallSymbol("paste"),
						this.rniP_BaseEnv ));
				final long deparseFun = rniProtect(this.rEngine.rniEval(
						this.rEngine.rniInstallSymbol("deparse"),
						this.rniP_BaseEnv ));
				
				final long exprSymbol = this.rEngine.rniInstallSymbol("expr");
				final long controlSymbol = this.rEngine.rniInstallSymbol("control");
				final long widthcutoffSymbol = this.rEngine.rniInstallSymbol("width.cutoff");
				final long collapseSymbol = this.rEngine.rniInstallSymbol("collapse");
				
				final long deparseControlValue = rniProtect(this.rEngine.rniPutStringArray(
						new String[] { "keepInteger", "keepNA" } ));
				final long deparseWidthcutoffValue = rniProtect(this.rEngine.rniPutIntArray(
						new int[] { 500 } ));
				final long collapseValue = rniProtect(this.rEngine.rniPutString(""));
				
				{	// function(x)paste(deparse(expr=args(name=x),control=c("keepInteger", "keepNA"),width.cutoff=500L),collapse="")
					final long argList = rniProtect(this.rEngine.rniCons(
							this.rniP_MissingArg, this.rniP_NULL,
							this.rniP_xSymbol, false ));
					final long argsFun = rniProtect(this.rEngine.rniEval(
							this.rEngine.rniInstallSymbol("args"),
							this.rniP_BaseEnv ));
					final long argsCall = rniProtect(this.rEngine.rniCons(
							argsFun, this.rEngine.rniCons(
									this.rniP_xSymbol, this.rniP_NULL,
									this.rniP_nameSymbol, false ),
							0, true ));
					final long deparseCall = rniProtect(this.rEngine.rniCons(
							deparseFun, this.rEngine.rniCons(
									argsCall, this.rEngine.rniCons(
											deparseControlValue, this.rEngine.rniCons(
													this.rEngine.rniPutIntArray(new int[] { 500 }), this.rniP_NULL,
													widthcutoffSymbol, false ),
											controlSymbol, false ),
									exprSymbol, false ),
							0, true ));
					final long body = this.rEngine.rniCons(
							pasteFun, this.rEngine.rniCons(
									deparseCall, this.rEngine.rniCons(
											collapseValue, this.rniP_NULL,
											collapseSymbol, false ),
									0, false ),
							0, true );
					
					this.rniP_headerFun = this.rEngine.rniEval(this.rEngine.rniCons(
							this.rniP_functionSymbol, this.rEngine.rniCons(
									argList, this.rEngine.rniCons(
											body, this.rniP_NULL,
											0, false ),
									0, false ),
							0, true ),
							this.rniP_BaseEnv );
					this.rEngine.rniPreserve(this.rniP_headerFun);
				}
				
				{	// paste(deparse(expr=x,control=c("keepInteger", "keepNA"),width.cutoff=500L),collapse="")
					final long deparseCall = rniProtect(this.rEngine.rniCons(
							deparseFun, this.rEngine.rniCons(
									this.rniP_xSymbol, this.rEngine.rniCons(
											deparseControlValue, this.rEngine.rniCons(
													deparseWidthcutoffValue, this.rniP_NULL,
													widthcutoffSymbol, false ),
											controlSymbol, false ),
									exprSymbol, false ),
							0, true ));
					this.rEngine.rniPreserve(deparseCall);
					this.rniP_deparseLineXCall = this.rEngine.rniCons(
							pasteFun, this.rEngine.rniCons(
									deparseCall, this.rEngine.rniCons(
											collapseValue, this.rniP_NULL,
											collapseSymbol, false ),
									0, false ),
							0, true );
					this.rEngine.rniPreserve(this.rniP_deparseLineXCall);
				}
			}
			this.rniP_slotNamesFun = this.rEngine.rniEval(
					this.rEngine.rniParse("methods::.slotNames", 1),
					this.rniP_BaseEnv );
			this.rEngine.rniPreserve(this.rniP_slotNamesFun);
			
			this.rniP_evalTryCatch_errorExpr = this.rEngine.rniCons(
					this.rEngine.rniEval(this.rEngine.rniParse("function(e){" +
							"s<-raw(5);" +
							"class(s)<-\".rj.eval.error\";" +
							"attr(s,\"error\")<-e;" +
							"attr(s,\"output\")<-paste(capture.output(print(e)),collapse=\"\\n\");" +
							"invisible(s);}", 1 ), 0 ), this.rniP_NULL,
					this.rniP_errorSymbol, false );
			this.rEngine.rniPreserve(this.rniP_evalTryCatch_errorExpr);
			
			this.rniP_evalTemp_classExpr = this.rEngine.rniParse("class(x);", 1);
			this.rEngine.rniPreserve(this.rniP_evalTemp_classExpr);
			this.rniP_evalTemp_rmExpr = this.rEngine.rniParse("rm(x);", 1);
			this.rEngine.rniPreserve(this.rniP_evalTemp_rmExpr);
			
			this.rniP_evalDummyExpr = this.rEngine.rniParse("1+1;", 1);
			this.rEngine.rniPreserve(this.rniP_evalDummyExpr);
			
			if (LOGGER.isLoggable(Level.FINER)) {
				final StringBuilder sb = new StringBuilder("Pointers:");
				
				final Field[] fields = getClass().getDeclaredFields();
				for (final Field field : fields) {
					final String name = field.getName();
					if (name.startsWith("rniP_") && Long.TYPE.equals(field.getType())) {
						sb.append("\n\t");
						sb.append(name.substring(5));
						sb.append(" = ");
						try {
							final long p = field.getLong(this);
							sb.append("0x");
							sb.append(Long.toHexString(p));
						}
						catch (final Exception e) {
							sb.append(e.getMessage());
						}
					}
				}
				
				LOGGER.log(Level.FINER, sb.toString());
			}
			
			if (this.rniP_tryCatchFun == 0) {
				LOGGER.log(Level.SEVERE, "Failed to initialize engine: Base functions are missing (check 'Renviron').");
				AbstractServerControl.exit(162);
				return;
			}
			
			loadPlatformData();
			
			if (this.rClassLoader.getOSType() == RJClassLoader.OS_WIN) {
				if (this.rArgs.contains("--internet2")) {
					this.rEngine.rniEval(this.rEngine.rniParse("utils::setInternet2(use=TRUE)", 1), 0);
				}
				if (this.rMemSize != 0) {
					final long rniP = this.rEngine.rniEval(this.rEngine.rniParse("utils::memory.limit()", 1), 0);
					if (rniP != 0) {
						final long memSizeMB = this.rMemSize / MEGA;
						final double[] array = this.rEngine.rniGetDoubleArray(rniP);
						if (array != null && array.length == 1 && memSizeMB > array[0]) {
							this.rEngine.rniEval(this.rEngine.rniParse("utils::memory.limit(size="+memSizeMB+")", 1), 0);
						}
					}
				}
			}
			
			this.graphics = new JRIServerGraphics(this, this.rEngine);
			
			this.hotMode = false;
			if (this.hotModeDelayed) {
				this.hotModeRequested = true;
			}
			if (!this.hotModeRequested) {
				return;
			}
		}
		finally {
			if (this.rniProtectedCounter > savedProtectedCounter) {
				this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
				this.rniProtectedCounter = savedProtectedCounter;
			}
			
			this.rEngine.addMainLoopCallbacks(JRIServer.this);
		}
		
		// if (this.hotModeRequested)
		rProcessJEvents(this.rEngine);
	}
	
	private void loadPlatformData() {
		try {
			for (final Entry<String, String> dataEntry : this.platformDataCommands.entrySet()) {
				final DataCmdItem dataCmd = internalEvalData(new DataCmdItem(DataCmdItem.EVAL_DATA,
						1, dataEntry.getValue(), null ));
				if (dataCmd != null && dataCmd.isOK()) {
					final RObject data = dataCmd.getData();
					if (data.getRObjectType() == RObject.TYPE_VECTOR) {
						switch (data.getData().getStoreType()) {
						case RStore.CHARACTER:
							if (data.getLength() == 1) {
								this.platformDataValues.put(dataEntry.getKey(), data.getData().get(0));
								continue;
							}
						}
					}
				}
				LOGGER.log(Level.WARNING, "The platform data item '" + dataEntry.getKey() + "' could not be created.");
			}
		}
		catch (final Throwable e) {
			LOGGER.log(Level.SEVERE, "An error occurred when loading platform data.", e);
		}
	}
	
	public Map<String, Object> getPlatformData() {
		return this.platformDataValues;
	}
	
	private String[] checkArgs(final String[] args) {
		final List<String> checked = new ArrayList<String>(args.length+1);
		boolean saveState = false;
		for (final String arg : args) {
			if (arg != null && arg.length() > 0) {
				// add other checks here
				if (arg.equals("--interactive")) {
					saveState = true;
				}
				else if (arg.startsWith("--max-cssize=")) {
					long size = s2long(arg.substring(13), 0);
					size = ((size + MEGA - 1) / MEGA) * MEGA;
					if (size >= 4 * MEGA) {
						this.rConfig.MainCStack_Size = size;
					}
				}
				else if (arg.startsWith("--max-mem-size=")) {
					final long size = s2long(arg.substring(15), 0);
					if (size > 0) {
						this.rMemSize = size;
					}
				}
				checked.add(arg);
			}
		}
		if (!saveState && this.rClassLoader.getOSType() != RJClassLoader.OS_WIN) {
			checked.add(0, "--interactive");
		}
		this.rArgs = checked;
		return checked.toArray(new String[checked.size()]);
	}
	
	public ConsoleEngine connect(final Client client, final Map<String, ? extends Object> properties) throws RemoteException {
		assert (client.slot == 0);
		this.clientLocks[client.slot].writeLock().lock();
		try {
			if (this.client0 == client) {
				return this.client0ExpRef;
			}
			final ConsoleEngine consoleEngine = new ConsoleEngineImpl(this.publicServer, this, client);
			final ConsoleEngine export = (ConsoleEngine) UnicastRemoteObject.exportObject(consoleEngine, 0);
			
			this.mainExchangeLock.lock();
			try {
				this.mainLoopS2CAnswerFail = 0;
				switch (this.mainLoopState) {
				case ENGINE_WAIT_FOR_CLIENT:
				case ENGINE_RUN_IN_R:
					// exit old client
					if (this.mainLoopClient0State == CLIENT_OK_WAIT) {
						this.mainLoopClient0State = CLIENT_CANCEL;
						this.mainExchangeClient.signalAll();
						while (this.mainLoopClient0State == CLIENT_CANCEL) {
							try {
								this.mainExchangeClient.awaitNanos(100000000L);
							}
							catch (final InterruptedException e) {}
						}
						// setup new client
						if (this.mainLoopClient0State != CLIENT_NONE) {
							throw new AssertionError();
						}
					}
					
					this.mainLoopBusyAtClient = true;
					this.mainLoopClient0State = CLIENT_OK;
					this.mainLoopS2CAnswerFail = 0;
					if (this.mainLoopS2CLastCommands[0].getItems() != null) {
						if (this.mainLoopS2CNextCommandsFirst[0] != null) {
							MainCmdItem last = this.mainLoopS2CLastCommands[0].getItems();
							while (last.next != null) {
								last = last.next;
							}
							last.next = this.mainLoopS2CNextCommandsFirst[0];
						}
						this.mainLoopS2CNextCommandsFirst[0] = this.mainLoopS2CLastCommands[0].getItems();
					}
					if (this.mainLoopS2CNextCommandsFirst[0] == null) {
						this.mainLoopS2CNextCommandsFirst[0] = this.mainLoopS2CNextCommandsLast[0] = this.mainLoopS2CRequest.get(this.mainLoopS2CRequest.size()-1);
					}
					// restore mainLoopS2CNextCommandsLast, if necessary
					if (this.mainLoopS2CNextCommandsLast[0] == null && this.mainLoopS2CNextCommandsFirst != null) {
						this.mainLoopS2CNextCommandsLast[0] = this.mainLoopS2CNextCommandsFirst[0];
						while (this.mainLoopS2CNextCommandsLast[0].next != null) {
							this.mainLoopS2CNextCommandsLast[0] = this.mainLoopS2CNextCommandsLast[0].next;
						}
					}
					// notify listener client
//					try {
//						new RjsStatusImpl2(V_CANCEL, S_DISCONNECTED, 
//								(this.currentUsername != null) ? ("user "+ this.currentUsername) : null);
//					}
//					catch (Throwable e) {}
					LOGGER.log(Level.INFO, "New client connected. New Client-State: 'Connected'.");
					
					setProperties(client.slot, properties, true);
					
					connectClient0(client, consoleEngine, export);
					return export;
				default:
					throw new IllegalStateException("R engine is not running.");
				}
			}
			catch (final Throwable e) {
				final String message = "An error occurred when connecting.";
				LOGGER.log(Level.SEVERE, message, e);
				if (export != null) {
					UnicastRemoteObject.unexportObject(export, true);
				}
				throw new RemoteException(message, e);
			}
			finally {
				this.mainExchangeLock.unlock();
			}
		}
		finally {
			this.clientLocks[client.slot].writeLock().unlock();
		}
	}
	
	public void disconnect(final Client client) throws RemoteException {
		assert (client.slot == 0);
		this.clientLocks[client.slot].writeLock().lock();
		try {
			checkClient(client);
			
			this.mainExchangeLock.lock();
			try {
				if (this.mainLoopClient0State == CLIENT_OK_WAIT) {
					// exit old client
					this.mainLoopClient0State = CLIENT_CANCEL;
					while (this.mainLoopClient0State == CLIENT_CANCEL) {
						this.mainExchangeClient.signalAll();
						try {
							this.mainExchangeClient.awaitNanos(100000000L);
						}
						catch (final InterruptedException e) {}
					}
					// setup new client
					if (this.mainLoopClient0State != CLIENT_NONE) {
						throw new AssertionError();
					}
				}
				else {
					this.mainLoopClient0State = CLIENT_NONE;
				}
				disconnectClient0();
			}
			finally {
				this.mainExchangeLock.unlock();
			}
		}
		finally {
			this.clientLocks[client.slot].writeLock().unlock();
		}
	}
	
	public RjsComObject runMainLoop(final Client client, final RjsComObject command) throws RemoteException {
		this.clientLocks[client.slot].readLock().lock();
		boolean clientLock = true;
		try {
			checkClient(client);
			
			this.mainLoopS2CLastCommands[client.slot].clear();
			
			switch ((command != null) ? command.getComType() : RjsComObject.T_MAIN_LIST) {
			
			case RjsComObject.T_PING:
				return RjsStatus.OK_STATUS;
			
			case RjsComObject.T_MAIN_LIST:
				final MainCmdC2SList mainC2SCmdList = (MainCmdC2SList) command;
				if (client.slot > 0 && mainC2SCmdList != null) {
					MainCmdItem item = mainC2SCmdList.getItems();
					while (item != null) {
						item.slot = client.slot;
						item = item.next;
					}
				}
				this.mainExchangeLock.lock();
				this.clientLocks[client.slot].readLock().unlock();
				clientLock = false;
				try {
					return internalMainCallbackFromClient(client.slot, mainC2SCmdList);
				}
				finally {
					this.mainExchangeLock.unlock();
				}
				
			case RjsComObject.T_CTRL:
				return internalCtrl(client.slot, (CtrlCmdItem) command);
				
			case RjsComObject.T_FILE_EXCHANGE:
				return command;
			
			default:
				throw new IllegalArgumentException("Unknown command: " + "0x"+Integer.toHexString(command.getComType()) + ".");
			
			}
		}
		finally {
			if (clientLock) {
				this.clientLocks[client.slot].readLock().unlock();
			}
		}
	}
	
	
	public RjsComObject runAsync(final Client client, final RjsComObject command) throws RemoteException {
		this.clientLocks[client.slot].readLock().lock();
		try {
			checkClient(client);
			
			if (command == null) {
				throw new IllegalArgumentException("Missing command.");
			}
			switch (command.getComType()) {
			
			case RjsComObject.T_PING:
				if (client.slot == 0) {
					this.client0LastPing = System.nanoTime();
				}
				return internalPing();
			
			case RjsComObject.T_CTRL:
				return internalCtrl(client.slot, (CtrlCmdItem) command);
			
			case RjsComObject.T_FILE_EXCHANGE:
				return command;
			
			default:
				throw new IllegalArgumentException("Unknown command: " + "0x"+Integer.toHexString(command.getComType()) + ".");
			}
		}
		finally {
			this.clientLocks[client.slot].readLock().unlock();
		}
	}
	
	private RjsStatus internalCtrl(final byte slot, final CtrlCmdItem command) {
		switch (command.getCtrlId()) {
		
		case CtrlCmdItem.REQUEST_CANCEL:
			try {
				if (this.mainInterruptLock.tryLock(1L, TimeUnit.SECONDS)) {
					try {
						this.rniInterrupted = true;
						this.rEngine.rniStop(0);
						return RjsStatus.OK_STATUS;
					}
					catch (final Throwable e) {
						LOGGER.log(Level.SEVERE, "An error occurred when trying to interrupt the R engine.", e);
						return new RjsStatus(RjsStatus.ERROR, CODE_CTRL_REQUEST_CANCEL | 0x2);
					}
					finally {
						this.mainInterruptLock.unlock();
					}
				}
			}
			catch (final InterruptedException e) {
				Thread.interrupted();
			}
			return new RjsStatus(RjsStatus.ERROR, CODE_CTRL_REQUEST_CANCEL | 0x1, "Timeout.");
		
		case CtrlCmdItem.REQUEST_HOT_MODE:
			this.mainExchangeLock.lock();
			try {
				if (!this.hotModeRequested) {
					if (this.hotMode) {
						this.hotModeRequested = true;
					}
					else {
						this.hotModeRequested = true;
						this.rEngine.rniSetProcessJEvents(1);
						this.mainExchangeR.signalAll();
					}
				}
				return RjsStatus.OK_STATUS;
			}
			catch (final Exception e) {
				LOGGER.log(Level.SEVERE, "An error occurred when requesting hot mode.", e);
				return new RjsStatus(RjsStatus.ERROR, CODE_CTRL_REQUEST_HOT_MODE | 0x2);
			}
			finally {
				this.mainExchangeLock.unlock();
			}
		}
		
		return new RjsStatus(RjsStatus.ERROR, CODE_CTRL_COMMON | 0x2);
	}
	
	private RjsStatus internalPing() {
		final Rengine r = this.rEngine;
		if (r.isAlive()) {
			return RjsStatus.OK_STATUS;
		}
		if (this.mainLoopState != ENGINE_STOPPED) {
			// invalid state
		}
		return new RjsStatus(RjsStatus.WARNING, S_STOPPED);
	}
	
	private RjsComObject internalMainCallbackFromClient(final byte slot, final MainCmdC2SList mainC2SCmdList) {
//		System.out.println("fromClient 1: " + mainC2SCmdList);
//		System.out.println("C2S: " + this.mainLoopC2SCommandFirst);
//		System.out.println("S2C: " + this.mainLoopS2CNextCommandsFirst[1]);
		
		if (slot == 0 && this.mainLoopClient0State != CLIENT_OK) {
			return new RjsStatus(RjsStatus.WARNING, S_DISCONNECTED);
		}
		if (this.mainLoopState == ENGINE_WAIT_FOR_CLIENT) {
			if (mainC2SCmdList == null && this.mainLoopS2CNextCommandsFirst[slot] == null) {
				if (slot == 0) {
					if (this.mainLoopS2CAnswerFail < 3) { // retry
						this.mainLoopS2CAnswerFail++;
						this.mainLoopS2CNextCommandsFirst[0] = this.mainLoopS2CNextCommandsLast[0] = this.mainLoopS2CRequest.get(this.mainLoopS2CRequest.size()-1);
						LOGGER.log(Level.WARNING, "Unanswered request - retry: " + this.mainLoopS2CNextCommandsLast[0]);
						// continue ANSWER
					}
					else { // fail
						this.mainLoopC2SCommandFirst = this.mainLoopS2CRequest.get(this.mainLoopS2CRequest.size()-1);
						this.mainLoopC2SCommandFirst.setAnswer(new RjsStatus(RjsStatus.ERROR, 0));
						this.mainLoopS2CNextCommandsFirst[0] = this.mainLoopS2CNextCommandsLast[0] = null;
						LOGGER.log(Level.SEVERE, "Unanswered request - skip: " + this.mainLoopC2SCommandFirst);
						// continue in R
					}
				}
				else {
					return new RjsStatus(RjsStatus.ERROR, RjsStatus.ERROR);
				}
			}
			else { // ok
				this.mainLoopS2CAnswerFail = 0;
			}
		}
		if (mainC2SCmdList != null) {
			this.rniInterrupted = false;
			if (this.mainLoopC2SCommandFirst == null) {
				this.mainLoopC2SCommandFirst = mainC2SCmdList.getItems();
			}
			else {
				MainCmdItem cmd = this.mainLoopC2SCommandFirst;
				while (cmd.next != null) {
					cmd = cmd.next;
				}
				cmd.next = mainC2SCmdList.getItems();
			}
		}
		
//			System.out.println("fromClient 2: " + mainC2SCmdList);
//			System.out.println("C2S: " + this.mainLoopC2SCommandFirst);
//			System.out.println("S2C: " + this.mainLoopS2CNextCommandsFirst[1]);
		
		this.mainExchangeR.signalAll();
		if (slot == 0) {
			this.mainLoopClient0State = CLIENT_OK_WAIT;
		}
		while (this.mainLoopS2CNextCommandsFirst[slot] == null
//					&& (this.mainLoopState != ENGINE_STOPPED)
				&& (this.mainLoopState == ENGINE_RUN_IN_R
						|| this.mainLoopC2SCommandFirst != null
						|| this.hotModeRequested)
				&& ((slot > 0)
						|| ( (this.mainLoopClient0State == CLIENT_OK_WAIT)
							&& (this.mainLoopStdOutSize == 0)
							&& (this.mainLoopBusyAtClient == this.mainLoopBusyAtServer) )
				)) {
			this.mainLoopClientListen++;
			try {
				this.mainExchangeClient.await(); // run in R
			}
			catch (final InterruptedException e) {}
			finally {
				this.mainLoopClientListen--;
			}
		}
		if (slot == 0 && this.mainLoopClient0State == CLIENT_OK_WAIT) {
			this.mainLoopClient0State = CLIENT_OK;
		}
		
//			System.out.println("fromClient 3: " + mainC2SCmdList);
//			System.out.println("C2S: " + this.mainLoopC2SCommandFirst);
//			System.out.println("S2C: " + this.mainLoopS2CNextCommandsFirst[1]);
		
		// answer
		if (slot > 0 || this.mainLoopClient0State == CLIENT_OK) {
			if (this.mainLoopStdOutSize > 0) {
				internalClearStdOutBuffer();
				this.mainLoopStdOutSize = 0;
			}
			if (this.mainLoopState == ENGINE_STOPPED && this.mainLoopS2CNextCommandsFirst[slot] == null) {
				return new RjsStatus(RjsStatus.INFO, S_STOPPED);
			}
			this.mainLoopBusyAtClient = this.mainLoopBusyAtServer;
			this.mainLoopS2CLastCommands[slot].setBusy(this.mainLoopBusyAtClient);
			this.mainLoopS2CLastCommands[slot].setObjects(this.mainLoopS2CNextCommandsFirst[slot]);
			this.mainLoopS2CNextCommandsFirst[slot] = null;
			return this.mainLoopS2CLastCommands[slot];
		}
		else {
			this.mainLoopClient0State = CLIENT_NONE;
			return new RjsStatus(RjsStatus.CANCEL, S_DISCONNECTED); 
		}
	}
	
	private MainCmdItem internalMainFromR(final MainCmdItem initialItem) {
		MainCmdItem item = initialItem;
		boolean initial = true;
		while (true) {
			this.mainExchangeLock.lock();
			try {
//				System.out.println("fromR 1: " + item);
//				System.out.println("C2S: " + this.mainLoopC2SCommandFirst);
//				System.out.println("S2C: " + this.mainLoopS2CNextCommandsFirst[1]);
				
				if (item != null) {
					if (this.mainLoopStdOutSize > 0) {
						internalClearStdOutBuffer();
						this.mainLoopStdOutSize = 0;
					}
					if (this.mainLoopS2CNextCommandsFirst[item.slot] == null) {
						this.mainLoopS2CNextCommandsFirst[item.slot] = this.mainLoopS2CNextCommandsLast[item.slot] = item;
					}
					else {
						this.mainLoopS2CNextCommandsLast[item.slot] = this.mainLoopS2CNextCommandsLast[item.slot].next = item;
					}
				}
				
				this.mainExchangeClient.signalAll();
				
				if (initial) {
					if (initialItem == null || !initialItem.waitForClient()) {
						return null;
					}
					initialItem.requestId = this.mainLoopS2CRequest.size();
					this.mainLoopS2CRequest.add(initialItem);
					initial = false;
				}
				
				// initial != null && initial.waitForClient()
				if (this.mainLoopState == ENGINE_STOPPED) {
					initialItem.setAnswer(new RjsStatus(RjsStatus.ERROR, S_STOPPED));
					return initialItem;
				}
				
				if (this.mainLoopC2SCommandFirst == null) {
					this.mainLoopState = ENGINE_WAIT_FOR_CLIENT;
					final int stackId = ++this.mainLoopServerStack;
					try {
						if (Thread.currentThread() == this.rEngine) {
							while ((this.mainLoopC2SCommandFirst == null && !this.hotModeRequested)
									|| this.mainLoopServerStack > stackId ) {
								int i = 0;
								final ServerRuntimePlugin[] plugins = this.pluginsList;
								this.mainExchangeLock.unlock();
								this.mainInterruptLock.lock();
								try {
									for (; i < plugins.length; i++) {
										plugins[i].rjIdle();
									}
									
									this.rEngine.rniIdle();
								}
								catch (final Throwable e) {
									if (i < plugins.length) {
										handlePluginError(plugins[i], e);
									}
								}
								finally {
									this.mainInterruptLock.unlock();
									this.mainExchangeLock.lock();
								}
								if ((this.mainLoopC2SCommandFirst != null || this.hotModeRequested)
										&& this.mainLoopServerStack <= stackId ) {
									break;
								}
								try {
									this.mainExchangeR.awaitNanos(50000000L);
								}
								catch (final InterruptedException e) {}
							}
						}
						else {
							// TODO log warning
							while (this.mainLoopC2SCommandFirst == null || this.mainLoopServerStack > stackId) {
								try {
									this.mainExchangeR.awaitNanos(50000000L);
								}
								catch (final InterruptedException e) {}
							}
						}
					}
					finally {
						this.mainLoopServerStack--;
						this.mainLoopState = ENGINE_RUN_IN_R;
					}
				}
				
				if (this.hotModeRequested) {
					item = null;
				}
				else {
					// initial != null && initial.waitForClient()
					// && this.mainLoopC2SCommandFirst != null
					item = this.mainLoopC2SCommandFirst;
					this.mainLoopC2SCommandFirst = this.mainLoopC2SCommandFirst.next;
					item.next = null;
					
	//				System.out.println("fromR 2: " + item);
	//				System.out.println("C2S: " + this.mainLoopC2SCommandFirst);
	//				System.out.println("S2C: " + this.mainLoopS2CNextCommandsFirst[1]);
					
					if (item.getCmdType() < MainCmdItem.T_S2C_C2S) {
						// ANSWER
						if (initialItem.requestId == item.requestId) {
							this.mainLoopS2CRequest.remove((initialItem.requestId));
							assert (this.mainLoopS2CRequest.size() == item.requestId);
							return item;
						}
						else {
							item = null;
							continue;
						}
					}
				}
			}
			finally {
				this.mainExchangeLock.unlock();
			}
			
			// initial != null && initial.waitForClient()
			// && this.mainLoopC2SCommandFirst != null
			// && this.mainLoopC2SCommandFirst.getCmdType() < MainCmdItem.T_S2C_C2S
//			System.out.println("fromR evalDATA");
			
			if (item == null) {
				rProcessJEvents(null);
				continue;
			}
			switch (item.getCmdType()) {
			
			case MainCmdItem.T_DATA_ITEM:
				item = internalEvalData((DataCmdItem) item);
				continue;
			case MainCmdItem.T_GRAPHICS_OP_ITEM:
				item = internalExecGraOp((GraOpCmdItem) item);
				continue;
				
			default:
				continue;
			
			}
		}
	}
	
	private void internalClearStdOutBuffer() {
		final MainCmdItem item;
		if (this.mainLoopStdOutSingle != null) {
			item = new ConsoleWriteOutCmdItem(this.mainLoopStdOutSingle);
			this.mainLoopStdOutSingle = null;
		}
		else {
			item = new ConsoleWriteOutCmdItem(new String(this.mainLoopStdOutBuffer, 0, this.mainLoopStdOutSize));
		}
		if (this.mainLoopS2CNextCommandsFirst[0] == null) {
			this.mainLoopS2CNextCommandsFirst[0] = this.mainLoopS2CNextCommandsLast[0] = item;
		}
		else {
			this.mainLoopS2CNextCommandsLast[0] = this.mainLoopS2CNextCommandsLast[0].next = item;
		}
	}
	
	
	public boolean inSafeMode() {
		return this.safeMode;
	}
	
	public int beginSafeMode() {
		if (this.safeMode) {
			return 0;
		}
		try {
			// TODO disable
			this.safeMode = true;
			return 1;
		}
		catch (final Exception e) {
			LOGGER.log(Level.SEVERE, "An error occurred when running 'beginSafeMode' command.", e);
			return -1;
		}
	}
	
	public void endSafeMode(final int mode) {
		if (mode > 0) {
			try {
				this.safeMode = false;
				// TODO enable
			}
			catch (final Exception e) {
				LOGGER.log(Level.SEVERE, "An error occurred when running 'endSafeMode' command.", e);
			}
		}
	}
	
	/**
	 * Executes an {@link DataCmdItem R data command} (assignment, evaluation, ...).
	 * Returns the result in the cmd object passed in, which is passed back out.
	 * 
	 * @param cmd the data command item
	 * @return the data command item with setted answer
	 */
	private DataCmdItem internalEvalData(final DataCmdItem cmd) {
		final byte savedSlot = this.currentSlot;
		this.currentSlot = cmd.slot;
		final boolean ownLock = this.rEngine.getRsync().safeLock();
		final int savedDepth = this.rniDepth;
		final int savedMaxDepth = this.rniMaxDepth;
		{	final byte depth = cmd.getDepth();
			this.rniDepth = 0;
			this.rniMaxDepth = (depth >= 0) ? depth : 128;
		}
		final int savedProtectedCounter = this.rniProtectedCounter;
		final int savedSafeMode = beginSafeMode();
		try {
			final String input = cmd.getDataText();
			if (input == null) {
				throw new IllegalStateException("Missing input.");
			}
			final RObject data = cmd.getData();
			DATA_CMD: switch (cmd.getOp()) {
			
			case DataCmdItem.EVAL_VOID: {
				if (this.rniInterrupted) {
					throw new CancellationException();
				}
				if (data == null) {
					rniEval(input);
				}
				else {
					rniEval(input, (RList) data);
				}
				if (this.rniInterrupted) {
					throw new CancellationException();
				}
				cmd.setAnswer(RjsStatus.OK_STATUS); }
				break DATA_CMD;
			
			case DataCmdItem.EVAL_DATA: {
				if (this.rniInterrupted) {
					throw new CancellationException();
				}
				final long objP;
				if (data == null) {
					objP = rniEval(input);
				}
				else {
					objP = rniEval(input, (RList) data);
				}
				if (this.rniInterrupted) {
					throw new CancellationException();
				}
				cmd.setAnswer(rniCreateDataObject(objP, cmd.getCmdOption()));
				break DATA_CMD; }
			
			case DataCmdItem.RESOLVE_DATA: {
				final long objP = Long.parseLong(input);
				if (objP != 0) {
					if (this.rniInterrupted) {
						throw new CancellationException();
					}
					cmd.setAnswer(rniCreateDataObject(objP, cmd.getCmdOption()));
				}
				else {
					cmd.setAnswer(new RjsStatus(RjsStatus.ERROR, (CODE_DATA_RESOLVE_DATA | 0x1),
							"Invalid reference." ));
				}
				break DATA_CMD; }
			
			case DataCmdItem.ASSIGN_DATA: {
				if (this.rniInterrupted) {
					throw new CancellationException();
				}
				rniAssign(input, data);
				cmd.setAnswer(RjsStatus.OK_STATUS);
				break DATA_CMD; }
			
			default:
				throw new IllegalStateException("Unsupported command.");
			
			}
			if (this.rniInterrupted) {
				throw new CancellationException();
			}
		}
		catch (final RjsException e) {
			cmd.setAnswer(e.getStatus());
		}
		catch (final CancellationException e) {
			cmd.setAnswer(RjsStatus.CANCEL_STATUS);
		}
		catch (final Throwable e) {
			final String message = "Eval data failed. Cmd:\n" + cmd.toString() + ".";
			LOGGER.log(Level.SEVERE, message, e);
			cmd.setAnswer(new RjsStatus(RjsStatus.ERROR, (CODE_DATA_COMMON | 0x1),
					"Internal server error (see server log)." ));
		}
		finally {
			this.currentSlot = savedSlot;
			endSafeMode(savedSafeMode);
			if (this.rniProtectedCounter > savedProtectedCounter) {
				this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
				this.rniProtectedCounter = savedProtectedCounter;
			}
			this.rniDepth = savedDepth;
			this.rniMaxDepth = savedMaxDepth;
			
			if (this.rniInterrupted || this.rniEvalTempAssigned) {
				this.mainInterruptLock.lock();
				try {
					if (this.rniInterrupted) {
						try {
							Thread.sleep(10);
						}
						catch (final InterruptedException e) {}
						this.rEngine.rniEval(this.rniP_evalDummyExpr, 0);
						this.rniInterrupted = false;
					}
					if (this.rniEvalTempAssigned) {
						this.rEngine.rniEval(this.rniP_evalTemp_rmExpr, this.rniP_RJTempEnv);
						this.rniEvalTempAssigned = false;
					}
				}
				finally {
					this.mainInterruptLock.unlock();
					if (ownLock) {
						this.rEngine.getRsync().unlock();
					}
				}
			}
			else {
				if (ownLock) {
					this.rEngine.getRsync().unlock();
				}
			}
		}
		return cmd.waitForClient() ? cmd : null;
	}
	
	private long rniProtect(final long p) {
		this.rEngine.rniProtect(p);
		this.rniProtectedCounter++;
		return p;
	}
	
	private long rniEval(final String expression) throws RjsException {
		final long exprP = rniResolveExpression(expression);
		return rniEvalExpr(exprP, (CODE_DATA_EVAL_DATA | 0x3));
	}
	
	private long rniEval(final String name, final RList args) throws RjsException {
		final long exprP = rniCreateFCall(name, args);
		return rniEvalExpr(exprP, (CODE_DATA_EVAL_DATA | 0x4));
	}
	
	private long rniCreateFCall(final String name, final RList args) throws RjsException {
		long argsP = this.rniP_NULL;
		for (int i = args.getLength() - 1; i >= 0; i--) {
			final String argName = args.getName(i);
			final RObject argValue = args.get(i);
			final long argValueP;
			if (argValue != null) {
				argValueP = rniAssignDataObject(argValue);
			}
			else {
				argValueP = this.rniP_MissingArg;
			}
			argsP = rniProtect(this.rEngine.rniCons(argValueP, argsP,
					(argName != null) ? this.rEngine.rniInstallSymbol(argName) : 0, false ));
		}
		long funP;
		if (name.indexOf(':') > 0) {
			funP = this.rEngine.rniParse(name, 1);
			long[] list;
			if (funP != 0 && (list = this.rEngine.rniGetVector(funP)) != null && list.length == 1) {
				funP = list[0];
			}
			else {
				throw new RjsException(CODE_DATA_COMMON | 0x4, "The reference to the function is invalid.");
			}
		}
		else {
			funP = this.rEngine.rniInstallSymbol(name);
		}
		return this.rEngine.rniCons(funP, argsP, 0, true);
	}
	
	/**
	 * Creates and assigns an {@link RObject RJ R object} to an expression (e.g. symbol) in R.
	 * 
	 * @param expression an expression the R object is assigned to
	 * @param obj an R object to assign
	 * @throws RjException
	*/ 
	private void rniAssign(final String expression, final RObject obj) throws RjsException {
		if (obj == null) {
			throw new RjsException((CODE_DATA_ASSIGN_DATA | 0x2),
					"The R object to assign is missing." );
		}
		long exprP = rniProtect(rniResolveExpression(expression));
		final long objP = rniAssignDataObject(obj);
		exprP = this.rEngine.rniCons(
				this.rniP_AssignSymbol, this.rEngine.rniCons(
						exprP, this.rEngine.rniCons(
								objP, this.rniP_NULL,
								0, false ),
						0, false ),
				0, true );
		rniEvalExpr(exprP, (CODE_DATA_ASSIGN_DATA | 0x3));
	}
	
	private long rniResolveExpression(final String expression) throws RjsException {
		final long exprP = this.rEngine.rniParse(expression, 1);
		if (this.rEngine.rniExpType(exprP) != REXP.EXPRSXP) {
			throw new RjsException((CODE_DATA_COMMON | 0x3),
					"The specified expression is invalid (syntax error)." );
		}
		final long[] expressionsP = this.rEngine.rniGetVector(exprP);
		if (expressionsP == null || expressionsP.length != 1) {
			throw new RjsException((CODE_DATA_COMMON | 0x3),
					"The specified expression is invalid (not a single expression)." );
		}
		return expressionsP[0];
	}
	
	private long rniEvalExpr(long exprP, final int code) throws RjsException {
		exprP = this.rEngine.rniCons(
				this.rniP_tryCatchFun, this.rEngine.rniCons(
						exprP, this.rniP_evalTryCatch_errorExpr,
						this.rniP_exprSymbol, false ),
				0, true );
		final long objP = this.rEngine.rniEval(exprP, 0);
		if (objP == 0) {
			if (this.rniInterrupted) {
				throw new CancellationException();
			}
			throw new IllegalStateException("JRI returned error code " + objP + " (pointer = 0x" + Long.toHexString(exprP) + ")");
		}
		this.rEngine.rniProtect(objP);
		this.rniProtectedCounter++;
		if (this.rEngine.rniExpType(objP) == REXP.RAWSXP) {
			final String className1 = this.rEngine.rniGetClassAttrString(objP);
			if (className1 != null && className1.equals(".rj.eval.error")) {
				String message = null;
				final long outputP = this.rEngine.rniGetAttr(objP, "output");
				if (outputP != 0) {
					message = this.rEngine.rniGetString(outputP);
				}
				if (message == null) {
					message = "<no information available>";
				}
				switch (code) {
				case (CODE_DATA_EVAL_DATA | 0x3):
					message = "An error occurred when evaluation the specified expression in R " + message + ".";
					break;
				case (CODE_DATA_EVAL_DATA | 0x4):
					message = "An error occurred when evaluation the function in R " + message + ".";
					break;
				case (CODE_DATA_ASSIGN_DATA | 0x3):
					message = "An error occurred when assigning the value to the specified expression in R " + message + ".";
					break;
				case (CODE_DATA_ASSIGN_DATA | 0x8):
					message = "An error occurred when instancing an S4 object in R " + message + ".";
					break;
				default:
					message = message + ".";
					break;
				}
				throw new RjsException(code, message);
			}
		}
		return objP;
	}
	
	/**
	 * Put an {@link RObject RJ R object} into JRI, and get back the pointer to the object
	 * (Java to R).
	 * 
	 * @param obj an R object
	 * @return long protected R pointer
	 * @throws RjsException 
	 */ 
	private long rniAssignDataObject(final RObject obj) throws RjsException {
		RStore names;
		long objP;
		switch(obj.getRObjectType()) {
		case RObject.TYPE_NULL:
		case RObject.TYPE_MISSING:
			return this.rniP_NULL;
		case RObject.TYPE_VECTOR:
			objP = rniAssignDataStore(obj.getData());
			return objP;
		case RObject.TYPE_ARRAY:
			objP = rniAssignDataStore(obj.getData());
			this.rEngine.rniSetAttr(objP, "dim",
					this.rEngine.rniPutIntArray(((JRIArrayImpl<?>) obj).getJRIDimArray()));
			return objP;
		case RObject.TYPE_DATAFRAME: {
			final RDataFrame list = (RDataFrame) obj;
			final int length = list.getLength();
			final long[] itemPs = new long[length];
			for (int i = 0; i < length; i++) {
				itemPs[i] = rniAssignDataStore(list.getColumn(i));
			}
			objP = rniProtect(this.rEngine.rniPutVector(itemPs));
			names = list.getNames();
			if (names != null) {
				this.rEngine.rniSetAttr(objP, "names", rniAssignDataStore(names));
			}
			names = list.getRowNames();
			if (names != null) {
				this.rEngine.rniSetAttr(objP, "row.names", rniAssignDataStore(names));
			}
			else {
				final int[] rownames = new int[list.getRowCount()];
				for (int i = 0; i < rownames.length; ) {
					rownames[i] = ++i;
				}
				this.rEngine.rniSetAttr(objP, "row.names", this.rEngine.rniPutIntArray(rownames));
			}
			this.rEngine.rniSetAttr(objP, "class", this.rniP_dataframeClassString);
			return objP; }
		case RObject.TYPE_LIST: {
			final RList list = (RList) obj;
			final int length = list.getLength();
			final long[] itemPs = new long[length];
			final int savedProtectedCounter = this.rniProtectedCounter;
			for (int i = 0; i < length; i++) {
				itemPs[i] = rniAssignDataObject(list.get(i));
			}
			if (this.rniProtectedCounter > savedProtectedCounter) {
				this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
				this.rniProtectedCounter = savedProtectedCounter;
			}
			objP = rniProtect(this.rEngine.rniPutVector(itemPs));
			names = list.getNames();
			if (names != null) {
				this.rEngine.rniSetAttr(objP, "names", rniAssignDataStore(names));
			}
			return objP; }
		case RObject.TYPE_REFERENCE:
			return ((RReference) obj).getHandle();
		case RObject.TYPE_S4OBJECT: {
			final RS4Object s4obj = (RS4Object) obj;
			objP = this.rniP_NULL;
			for (int i = s4obj.getLength()-1; i >= 0; i--) {
				final RObject slotObj = s4obj.get(i);
				if (slotObj != null && slotObj.getRObjectType() != RObject.TYPE_MISSING) {
					objP = rniProtect(this.rEngine.rniCons(
							rniAssignDataObject(slotObj), objP,
							this.rEngine.rniInstallSymbol(s4obj.getName(i)), false ));
				}
			}
			return rniProtect(rniEvalExpr(this.rEngine.rniCons(
					this.rniP_newSymbol, this.rEngine.rniCons(
							this.rEngine.rniPutString(s4obj.getRClassName()), objP,
							this.rniP_ClassSymbol, false ),
					0, true ),
					(CODE_DATA_ASSIGN_DATA | 0x8) )); }
		case RObject.TYPE_LANGUAGE: {
			final RLanguage lang = (RLanguage) obj;
			if (lang.getLanguageType() == RLanguage.NAME) {
				return this.rEngine.rniInstallSymbol(lang.getSource());
			}
			objP = this.rEngine.rniParse(lang.getSource(), -1);
			if (objP == 0) {
				throw new RjsException(CODE_DATA_ASSIGN_DATA | 0x9, "The language data is invalid.");
			}
			if (lang.getLanguageType() == RLanguage.CALL) {
				final long[] list = this.rEngine.rniGetVector(objP);
				if (list != null && list.length == 1
						&& this.rEngine.rniExpType(list[0]) == REXP.LANGSXP ) {
					return rniProtect(list[0]);
				}
			}
			else {
				return rniProtect(objP);
			}
			break; }
		default:
			break;
		}
		throw new RjsException((CODE_DATA_ASSIGN_DATA | 0x7),
				"The assignment for R objects of type " + RDataUtil.getObjectTypeName(obj.getRObjectType()) + " is not yet supported." );
	}
	
	private long rniAssignDataStore(final RStore data) {
		switch (data.getStoreType()) {
		case RStore.LOGICAL:
			return rniProtect(this.rEngine.rniPutBoolArrayI(
					((JRILogicalDataImpl) data).getJRIValueArray() ));
		case RStore.INTEGER:
			return rniProtect(this.rEngine.rniPutIntArray(
					((JRIIntegerDataImpl) data).getJRIValueArray() ));
		case RStore.NUMERIC:
			return rniProtect(this.rEngine.rniPutDoubleArray(
					((JRINumericDataImpl) data).getJRIValueArray() ));
		case RStore.COMPLEX: {
			final JRIComplexDataImpl complex = (JRIComplexDataImpl) data;
			final long realP = rniProtect(this.rEngine.rniPutDoubleArray(complex.getJRIRealValueArray()));
			final long imaginaryP = this.rEngine.rniPutDoubleArray(complex.getJRIImaginaryValueArray());
			return rniProtect(this.rEngine.rniEval(this.rEngine.rniCons(
					this.rniP_complexFun, this.rEngine.rniCons(
							realP, this.rEngine.rniCons(
									imaginaryP, this.rniP_NULL,
									this.rniP_imaginarySymbol, false ),
							this.rniP_realSymbol, false ),
					0, true ),
					this.rniP_BaseEnv )); }
		case RStore.CHARACTER:
			return rniProtect(this.rEngine.rniPutStringArray(
					((JRICharacterDataImpl) data).getJRIValueArray() ));
		case RStore.RAW:
			return rniProtect(this.rEngine.rniPutRawArray(
					((JRIRawDataImpl) data).getJRIValueArray() ));
		case RStore.FACTOR: {
			final JRIFactorDataImpl factor = (JRIFactorDataImpl) data;
			final long objP = rniProtect(this.rEngine.rniPutIntArray(factor.getJRIValueArray()));
			this.rEngine.rniSetAttr(objP, "levels",
					this.rEngine.rniPutStringArray(factor.getJRILevelsArray()) );
			this.rEngine.rniSetAttr(objP, "class",
					factor.isOrdered() ? this.rniP_orderedClassString : this.rniP_factorClassString );
			return objP; }
		default:
			throw new UnsupportedOperationException();
		}
	}
	
	private RObject rniCreateDataObject(final long objP, final int flags) {
		if (this.rniMaxDepth > 0) {
			return rniCreateDataObject(objP, flags, EVAL_MODE_FORCE);
		}
		else {
			final RObject rObject = rniCreateDataObject(objP, (flags | F_ONLY_STRUCT), EVAL_MODE_FORCE);
			return new RReferenceImpl(objP, rObject.getRObjectType(), rObject.getRClassName());
		}
	}
	
	/**
	 * Returns {@link RObject RJ/R object} for the given R pointer
	 * (R to Java).
	 * 
	 * @param objP a valid pointer to an object in R
	 * @param objTmp an optional R expression pointing to the same object in R
	 * @param flags to configure the data to create
	 * @param force forces the creation of the object (ignoring the depth etc.)
	 * @return new created R object
	 */ 
	private RObject rniCreateDataObject(long objP, final int flags, final byte mode) {
		if (mode == EVAL_MODE_DEFAULT && (this.rniDepth >= this.rniMaxDepth)) {
			return null;
		}
		this.rniDepth++;
		try {
			int rType = this.rEngine.rniExpType(objP);
			if (rType == REXP.PROMSXP) {
				objP = this.rEngine.rniGetPromise(objP, 
						((flags & RObjectFactory.F_LOAD_PROMISE) != 0) ? 2 : 1);
				if (objP == 0) {
					return RPromise.INSTANCE;
				}
				rType = this.rEngine.rniExpType(objP);
			}
			switch (rType) {
			case REXP.NILSXP:
				return RNull.INSTANCE;
			
			case REXP.LGLSXP: { // logical vector / array
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.LGLSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIArrayImpl<RLogicalStore>(
									RObjectFactoryImpl.LOGI_STRUCT_DUMMY,
									className1, dim ) :
							new JRIArrayImpl<RLogicalStore>(
									new JRILogicalDataImpl(this.rEngine.rniGetBoolArrayI(objP)),
									className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RLogicalStore>(
									RObjectFactoryImpl.LOGI_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RLogicalStore>(
									new JRILogicalDataImpl(this.rEngine.rniGetBoolArrayI(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.INTSXP: { // integer vector / array
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.INTSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				if (className1 != null
						&& (className1.equals("factor")
								|| className1.equals("ordered")
								|| this.rEngine.rniInherits(objP, "factor")) ) {
					final String[] levels;
					{	final long levelsP = this.rEngine.rniGetAttrBySym(objP, this.rniP_levelsSymbol);
						levels = (levelsP != 0) ? this.rEngine.rniGetStringArray(levelsP) : null;
					}
					if (levels != null) {
						final boolean isOrdered = className1.equals("ordered") || this.rEngine.rniInherits(objP, "ordered");
						final RFactorStore factorData = ((flags & F_ONLY_STRUCT) != 0) ?
								new RFactorDataStruct(isOrdered, levels.length) :
								new RFactorDataImpl(this.rEngine.rniGetIntArray(objP), isOrdered, levels);
						return ((flags & F_ONLY_STRUCT) != 0) ?
								new JRIVectorImpl<RIntegerStore>(
										factorData,
										this.rEngine.rniGetLength(objP), className1, null ) :
								new JRIVectorImpl<RIntegerStore>(
										factorData,
										className1, rniGetNames(objP) );
					}
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIArrayImpl<RIntegerStore>(
									RObjectFactoryImpl.INT_STRUCT_DUMMY,
									className1, dim ) :
							new JRIArrayImpl<RIntegerStore>(
									new JRIIntegerDataImpl(this.rEngine.rniGetIntArray(objP)),
									className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RIntegerStore>(
									RObjectFactoryImpl.INT_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RIntegerStore>(
									new JRIIntegerDataImpl(this.rEngine.rniGetIntArray(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.REALSXP: { // numeric vector / array
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.REALSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIArrayImpl<RNumericStore>(
									RObjectFactoryImpl.NUM_STRUCT_DUMMY,
									className1, dim ) :
							new JRIArrayImpl<RNumericStore>(
									new JRINumericDataImpl(this.rEngine.rniGetDoubleArray(objP)),
									className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RNumericStore>(
									RObjectFactoryImpl.NUM_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RNumericStore>(
									new JRINumericDataImpl(this.rEngine.rniGetDoubleArray(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.CPLXSXP: { // complex vector / array
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.CPLXSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
						new JRIArrayImpl<RComplexStore>(
								RObjectFactoryImpl.CPLX_STRUCT_DUMMY,
								className1, dim ) :
						new JRIArrayImpl<RComplexStore>(
								new JRIComplexDataImpl(rniGetComplexRe(objP), rniGetComplexIm(objP)),
								className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RComplexStore>(
									RObjectFactoryImpl.CPLX_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RComplexStore>(
									new JRIComplexDataImpl(rniGetComplexRe(objP), rniGetComplexIm(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.STRSXP: { // character vector / array
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.STRSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIArrayImpl<RCharacterStore>(
									RObjectFactoryImpl.CHR_STRUCT_DUMMY,
									className1, dim ) :
							new JRIArrayImpl<RCharacterStore>(
									new JRICharacterDataImpl(this.rEngine.rniGetStringArray(objP)),
									className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RCharacterStore>(
									RObjectFactoryImpl.CHR_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RCharacterStore>(
									new JRICharacterDataImpl(this.rEngine.rniGetStringArray(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.RAWSXP: { // raw/byte vector
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					if (this.rEngine.rniIsS4(objP)) {
						return rniCreateS4Obj(objP, REXP.RAWSXP, flags);
					}
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final int[] dim = this.rEngine.rniGetArrayDim(objP);
				
				if (dim != null) {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIArrayImpl<RRawStore>(
									RObjectFactoryImpl.RAW_STRUCT_DUMMY,
									className1, dim ) :
							new JRIArrayImpl<RRawStore>(
									new JRIRawDataImpl(this.rEngine.rniGetRawArray(objP)),
									className1, dim, rniGetDimNames(objP, dim.length) );
				}
				else {
					return ((flags & F_ONLY_STRUCT) != 0) ?
							new JRIVectorImpl<RRawStore>(
									RObjectFactoryImpl.RAW_STRUCT_DUMMY,
									this.rEngine.rniGetLength(objP), className1, null ) :
							new JRIVectorImpl<RRawStore>(
									new JRIRawDataImpl(this.rEngine.rniGetRawArray(objP)),
									className1, rniGetNames(objP) );
				}
			}
			case REXP.VECSXP: { // generic vector / list
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				
				final String[] itemNames = rniGetNames(objP);
				
				final long[] itemP = this.rEngine.rniGetVector(objP);
				final RObject[] itemObjects = new RObject[itemP.length];
				DATA_FRAME: if (itemNames != null && className1 != null &&
						(className1.equals("data.frame") || this.rEngine.rniInherits(objP, "data.frame")) ) {
					int length = -1;
					for (int i = 0; i < itemP.length; i++) {
						if (this.rniInterrupted) {
							throw new CancellationException();
						}
						itemObjects[i] = rniCreateDataObject(itemP[i], flags, EVAL_MODE_FORCE);
						if (itemObjects[i] == null || itemObjects[i].getRObjectType() != RObject.TYPE_VECTOR) {
							break DATA_FRAME;
						}
						else if (length == -1) {
							length = itemObjects[i].getLength();
						}
						else if (length != itemObjects[i].getLength()){
							break DATA_FRAME;
						}
					}
					final String[] rowNames = ((flags & F_ONLY_STRUCT) != 0) ? null : rniGetRowNames(objP);
					if (rowNames != null && length != -1 && rowNames.length != length) {
						break DATA_FRAME;
					}
					return new JRIDataFrameImpl(itemObjects, className1, itemNames, rowNames);
				}
				if ((flags & F_ONLY_STRUCT) != 0 && itemP.length > this.rniListsMaxLength) {
					return new JRIListImpl(itemP.length, className1, itemNames);
				}
				for (int i = 0; i < itemP.length; i++) {
					if (this.rniInterrupted) {
						throw new CancellationException();
					}
					itemObjects[i] = rniCreateDataObject(itemP[i], flags, EVAL_MODE_DEFAULT);
				}
				return new JRIListImpl(itemObjects, className1, itemNames);
			}
			case REXP.LISTSXP:   // pairlist
			/*case REXP.LANGSXP: */{
				String className1;
				if (mode == EVAL_MODE_DATASLOT
						|| (className1 = this.rEngine.rniGetClassAttrString(objP)) == null ) {
					className1 = RObject.CLASSNAME_PAIRLIST;
				}
				
				long cdr = objP;
				final int length = this.rEngine.rniGetLength(objP);
				final String[] itemNames = new String[length];
				final RObject[] itemObjects = new RObject[length];
				for (int i = 0; i < length && cdr != 0; i++) {
					if (this.rniInterrupted) {
						throw new CancellationException();
					}
					final long car = this.rEngine.rniCAR(cdr);
					final long tag = this.rEngine.rniTAG(cdr);
					itemNames[i] = (tag != 0) ? this.rEngine.rniGetSymbolName(tag) : null;
					itemObjects[i] = rniCreateDataObject(car, flags, EVAL_MODE_DEFAULT);
					cdr = this.rEngine.rniCDR(cdr);
				}
				return new JRIListImpl(itemObjects, className1, itemNames);
			}
			case REXP.ENVSXP: {
				if (this.rniDepth > 1 && (flags & RObjectFactory.F_LOAD_ENVIR) == 0) {
					return new RReferenceImpl(objP, RObject.TYPE_REFERENCE, "environment");
				}
				final String[] names = this.rEngine.rniGetStringArray(this.rEngine.rniListEnv(objP, true));
				if (names != null) {
					final String className1;
					if (mode != EVAL_MODE_DATASLOT) {
						className1 = this.rEngine.rniGetClassAttrString(objP);
					}
					else {
						className1 = null;
					}
					
					// env name
					String name;
					final long nameP;
					if ((nameP = this.rEngine.rniEval(this.rEngine.rniCons(
									this.rniP_envNameFun, this.rEngine.rniCons(
											objP, this.rniP_NULL,
											this.rniP_envSymbol, false ),
									0, true ),
									this.rniP_BaseEnv )) == 0
							|| (name = this.rEngine.rniGetString(nameP)) == null ) {
						name = "";
					}
					
					if ((objP == this.rniP_AutoloadEnv)
							|| names.length > this.rniEnvsMaxLength) {
						return new JRIEnvironmentImpl(name, objP, null, null, names.length, className1);
					}
					
					final RObject[] itemObjects = new RObject[names.length];
					for (int i = 0; i < names.length; i++) {
						if (this.rniInterrupted) {
							throw new CancellationException();
						}
						final long itemP = this.rEngine.rniGetVar(objP, names[i]);
						if (itemP != 0) {
							itemObjects[i] = rniCreateDataObject(itemP, flags, EVAL_MODE_DEFAULT);
							continue;
						}
						else {
							itemObjects[i] = RMissing.INSTANCE;
							continue;
						}
					}
					return new JRIEnvironmentImpl(name, objP, itemObjects, names, names.length, className1);
				}
				break;
			}
			case REXP.CLOSXP: {
				if (mode != EVAL_MODE_DATASLOT && this.rEngine.rniIsS4(objP)) {
					return rniCreateS4Obj(objP, REXP.CLOSXP, flags);
				}
				
				final String header = rniGetFHeader(objP);
				return new RFunctionImpl(header);
			}
			case REXP.SPECIALSXP:
			case REXP.BUILTINSXP: {
				final String header = rniGetFHeader(objP);
				return new RFunctionImpl(header);
			}
			case REXP.S4SXP: {
				if (mode != EVAL_MODE_DATASLOT) {
					return rniCreateS4Obj(objP, REXP.S4SXP, flags);
				}
				break; // invalid
			}
			case REXP.SYMSXP: {
				if (objP == this.rniP_MissingArg) {
					return RMissing.INSTANCE;
				}
				return ((flags & F_ONLY_STRUCT) != 0) ? 
						new JRILanguageImpl(RLanguage.NAME, null) :
						new JRILanguageImpl(RLanguage.NAME, this.rEngine.rniGetSymbolName(objP), null);
			}
			case REXP.LANGSXP: {
				final String className1;
				if (mode != EVAL_MODE_DATASLOT) {
					className1 = this.rEngine.rniGetClassAttrString(objP);
				}
				else {
					className1 = null;
				}
				return ((flags & F_ONLY_STRUCT) != 0) ? 
						new JRILanguageImpl(RLanguage.CALL, className1) :
						new JRILanguageImpl(RLanguage.CALL, rniGetSource(objP), className1);
			}
			case REXP.EXPRSXP: {
				String className1;
				if (mode == EVAL_MODE_DATASLOT
						|| (className1 = this.rEngine.rniGetClassAttrString(objP)) == null ) {
					className1 = null;
				}
				return ((flags & F_ONLY_STRUCT) != 0) ? 
						new JRILanguageImpl(RLanguage.EXPRESSION, className1) :
						new JRILanguageImpl(RLanguage.EXPRESSION, rniGetSource(objP), className1);
			}
			case REXP.EXTPTRSXP: {
				String className1;
				if (mode == EVAL_MODE_DATASLOT
						|| (className1 = this.rEngine.rniGetClassAttrString(objP)) == null ) {
					className1 = "externalptr";
				}
				return new ROtherImpl(className1);
			}
			}
//				final long classP = this.rEngine.rniEval(this.rEngine.rniCons(this.rniP_classFun,
//						this.rEngine.rniCons(objP, this.rniP_NULL, this.rniP_xSymbol, false), 0, true),
//						this.rniP_BaseEnv);
			{	// Other type and fallback
				final String className1 = rniGetClassSave(objP);
				return new ROtherImpl(className1);
			}
		}
		finally {
			this.rniDepth--;
		}
	}
	
	private RObject rniCreateS4Obj(final long objP, final int rType, final int flags) {
		final long classP = this.rEngine.rniGetAttrBySym(objP, this.rniP_classSymbol);
		String className = null;
		if (classP != 0 && classP != this.rniP_NULL) {
			className = this.rEngine.rniGetString(classP);
			final long slotNamesP = this.rEngine.rniEval(this.rEngine.rniCons(
					this.rniP_slotNamesFun, this.rEngine.rniCons(
							classP, this.rniP_NULL,
							this.rniP_xSymbol, false ),
					0, true ),
					0 );
			if (slotNamesP != 0) {
				final String[] slotNames = this.rEngine.rniGetStringArray(slotNamesP);
				if (slotNames != null && slotNames.length > 0) {
					final RObject[] slotValues = new RObject[slotNames.length];
					for (int i = 0; i < slotNames.length; i++) {
						if (this.rniInterrupted) {
							throw new CancellationException();
						}
						if (".Data".equals(slotNames[i])) {
							slotValues[i] = rniCreateDataObject(objP, flags, EVAL_MODE_DATASLOT);
							if (className == null && slotValues[i] != null) {
								className = slotValues[i].getRClassName();
							}
							continue;
						}
						else {
							final long slotValueP;
							if ((slotValueP = this.rEngine.rniGetAttr(objP, slotNames[i])) != 0) {
								slotValues[i] = rniCreateDataObject(slotValueP, flags, EVAL_MODE_FORCE);
								continue;
							}
							else {
								slotValues[i] = RMissing.INSTANCE;
								continue;
							}
						}
					}
					if (className == null) {
						className = rniGetClassSave(objP);
					}
					return new RS4ObjectImpl(className, slotNames, slotValues);
				}
			}
		}
		if (rType != REXP.S4SXP) {
			final RObject dataSlot = rniCreateDataObject(objP, flags, EVAL_MODE_DATASLOT);
			if (dataSlot != null) {
				if (className == null) {
					className = dataSlot.getRClassName();
					if (className == null) {
						className = rniGetClassSave(objP);
					}
				}
				return new RS4ObjectImpl(className, DATA_NAME_ARRAY, new RObject[] { dataSlot });
			}
			if (className == null) {
				className = rniGetClassSave(objP);
			}
		}
		else if (className == null) {
			className = "S4";
		}
		return new RS4ObjectImpl(className, EMPTY_STRING_ARRAY, EMPTY_ROBJECT_ARRAY);
	}
	
	private String rniGetClassSave(final long objP) {
		if (objP != 0) {
			final String className1;
			final long classP;
			this.rniEvalTempAssigned = true;
			if (this.rEngine.rniAssign("x", objP, this.rniP_RJTempEnv)
					&& (classP = this.rEngine.rniEval(this.rniP_evalTemp_classExpr, this.rniP_RJTempEnv)) != 0
					&& (className1 = this.rEngine.rniGetString(classP)) != null ) {
				return className1;
			}
		}
		return "<unknown>";
	}
	
	private String[] rniGetNames(final long objP) {
		final long namesP = this.rEngine.rniGetAttrBySym(objP, this.rniP_namesSymbol);
		return (namesP != 0) ? this.rEngine.rniGetStringArray(namesP) : null;
	}
	
	private SimpleRListImpl<RStore> rniGetDimNames(final long objP, final int length) {
		final long namesP = this.rEngine.rniGetAttrBySym(objP, this.rniP_dimNamesSymbol);
		if (this.rEngine.rniExpType(namesP) == REXP.VECSXP) {
			final long[] names1P = this.rEngine.rniGetVector(namesP);
			if (names1P != null && names1P.length == length) {
				String[] s = rniGetNames(namesP);
				final RCharacterStore names0 = (s != null) ? new RCharacterDataImpl(s) :
					new RCharacterDataImpl(names1P.length);
				final RCharacterStore[] names1 = new RCharacterStore[names1P.length];
				for (int i = 0; i < names1P.length; i++) {
					s = this.rEngine.rniGetStringArray(names1P[i]);
					if (s != null) {
						names1[i] = new RCharacterDataImpl(s);
					}
				}
				return new SimpleRListImpl<RStore>(names0, names1);
			}
		}
		return null;
	}
	
	private String[] rniGetRowNames(final long objP) {
		final long namesP = this.rEngine.rniGetAttrBySym(objP, this.rniP_rowNamesSymbol);
		return (namesP != 0) ? this.rEngine.rniGetStringArray(namesP) : null;
	}
	
	private double[] rniGetComplexRe(final long objP) {
		final double[] num;
		final long numP = this.rEngine.rniEval(this.rEngine.rniCons(
				this.rniP_ReFun, this.rEngine.rniCons(
						objP, this.rniP_NULL,
						this.rniP_zSymbol, false ),
				0, true ),
				this.rniP_BaseEnv );
		if (numP == 0) {
			if (this.rniInterrupted) {
				throw new CancellationException();
			}
			throw new IllegalStateException("JRI returned error code " + numP);
		}
		if ((num = this.rEngine.rniGetDoubleArray(numP)) != null) {
			return num;
		}
		throw new IllegalStateException();
	}
	
	private double[] rniGetComplexIm(final long objP) {
		final double[] num;
		final long numP = this.rEngine.rniEval(this.rEngine.rniCons(
				this.rniP_ImFun, this.rEngine.rniCons(
						objP, this.rniP_NULL,
						this.rniP_zSymbol, false ),
				0, true ),
				this.rniP_BaseEnv );
		if (numP == 0) {
			if (this.rniInterrupted) {
				throw new CancellationException();
			}
			throw new IllegalStateException("JRI returned error code " + numP);
		}
		if ((num = this.rEngine.rniGetDoubleArray(numP)) != null) {
			return num;
		}
		throw new IllegalStateException();
	}
	
	private String rniGetFHeader(final long objP) {
		final String args;
		final long argsP;
		if ((argsP = this.rEngine.rniEval(this.rEngine.rniCons(
						this.rniP_headerFun, this.rEngine.rniCons(
								objP, this.rniP_NULL,
								this.rniP_xSymbol, false ),
						0, true ),
						this.rniP_BaseEnv )) != 0
				&& (args = this.rEngine.rniGetString(argsP)) != null
				&& args.length() >= 11 ) { // "function ()".length
//			return args.substring(9,);
			return args;
		}
		return null;
	}
	
	private String rniGetSource(final long objP) {
		this.rniEvalTempAssigned = true;
		if (this.rEngine.rniAssign("x", objP, this.rniP_RJTempEnv)) {
			final String args;
			final long argsP;
			if ((argsP = this.rEngine.rniEval(this.rniP_deparseLineXCall, this.rniP_RJTempEnv)) != 0
					&& (args = this.rEngine.rniGetString(argsP)) != null
					&& args.length() > 0 ) {
	//			return args.substring(9,);
				return args;
			}
		}
		return null;
	}
	
	
	/**
	 * Performs a graphics operations
	 * Returns the result in the cmd object passed in, which is passed back out.
	 * 
	 * @param cmd the command item
	 * @return the data command item with setted answer
	 */
	private GraOpCmdItem internalExecGraOp(final GraOpCmdItem cmd) {
		final byte savedSlot = this.currentSlot;
		this.currentSlot = cmd.slot;
		final int savedProtectedCounter = this.rniProtectedCounter;
		final int savedSafeMode = beginSafeMode();
		try {
			CMD_OP: switch (cmd.getOp()) {
			
			case GraOp.OP_CLOSE:
				cmd.setAnswer(this.graphics.closeGraphic(cmd.getDevId()));
				break CMD_OP;
			case GraOp.OP_REQUEST_RESIZE:
				cmd.setAnswer(this.graphics.resizeGraphic(cmd.getDevId()));
				break CMD_OP;
				
			case GraOp.OP_CONVERT_DEV2USER: {
				final Coord coord = (Coord) cmd.getData();
				final RjsStatus status = this.graphics.convertDev2User(cmd.getDevId(), coord);
				if (status.getSeverity() == RjsStatus.OK) {
					cmd.setAnswer(coord);
				}
				else {
					cmd.setAnswer(status);
				}
				break CMD_OP; }
			case GraOp.OP_CONVERT_USER2DEV: {
				final Coord coord = (Coord) cmd.getData();
				final RjsStatus status = this.graphics.convertUser2Dev(cmd.getDevId(), coord);
				if (status.getSeverity() == RjsStatus.OK) {
					cmd.setAnswer(coord);
				}
				else {
					cmd.setAnswer(status);
				}
				break CMD_OP; }
			
			default:
				throw new IllegalStateException("Unsupported graphics operation " + cmd.toString());
			
			}
			return cmd;
		}
//		catch (final RjsException e) {
//			cmd.setAnswer(e.getStatus());
//			return cmd;
//		}
		catch (final CancellationException e) {
			cmd.setAnswer(RjsStatus.CANCEL_STATUS);
			return cmd;
		}
		catch (final Throwable e) {
			final String message = "Eval data failed. Cmd:\n" + cmd.toString() + ".";
			LOGGER.log(Level.SEVERE, message, e);
			cmd.setAnswer(new RjsStatus(RjsStatus.ERROR, (CODE_DATA_COMMON | 0x1),
					"Internal server error (see server log)." ));
			return cmd;
		}
		finally {
			this.currentSlot = savedSlot;
			endSafeMode(savedSafeMode);
			if (this.rniProtectedCounter > savedProtectedCounter) {
				this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
				this.rniProtectedCounter = savedProtectedCounter;
			}
		}
	}
	
	
	public String rReadConsole(final Rengine re, final String prompt, final int addToHistory) {
		if (this.safeMode) {
			if (prompt.startsWith("Browse")) {
				return "c\n";
			}
			return "\n";
		}
		final MainCmdItem cmd = internalMainFromR(new ConsoleReadCmdItem(
				(addToHistory == 1) ? V_TRUE : V_FALSE, prompt ));
		if (cmd.isOK()) {
			return cmd.getDataText();
		}
		return "\n";
	}
	
	public void rWriteConsole(final Rengine re, final String text, final int type) {
		if (type == 0) {
			this.mainExchangeLock.lock();
			try {
				// first
				if (this.mainLoopStdOutSize == 0) {
					this.mainLoopStdOutSingle = text;
					this.mainLoopStdOutSize = text.length();
				}
				
				// buffer full
				else if (this.mainLoopStdOutSize + text.length() > STDOUT_BUFFER_SIZE) {
					internalClearStdOutBuffer();
					this.mainLoopStdOutSingle = text;
					this.mainLoopStdOutSize = text.length();
				}
				
				// add to buffer
				else {
					if (this.mainLoopStdOutSingle != null) {
						this.mainLoopStdOutSingle.getChars(0, this.mainLoopStdOutSingle.length(),
								this.mainLoopStdOutBuffer, 0 );
						this.mainLoopStdOutSingle = null;
					}
					text.getChars(0, text.length(), this.mainLoopStdOutBuffer, this.mainLoopStdOutSize);
					this.mainLoopStdOutSize += text.length();
				}
				
				if (this.mainLoopClientListen > 0) {
					this.mainExchangeClient.signalAll();
				}
				return;
			}
			finally {
				this.mainExchangeLock.unlock();
			}
		}
		else {
			internalMainFromR(new ConsoleWriteErrCmdItem(text));
		}
	}
	
	public void rFlushConsole(final Rengine re) {
		internalMainFromR(null);
	}
	
	public void rBusy(final Rengine re, final int which) {
		this.mainLoopBusyAtServer = (which == 1);
		internalMainFromR(null);
	}
	
	public void rShowMessage(final Rengine re, final String message) {
		internalMainFromR(new ConsoleMessageCmdItem(message));
	}
	
	public String rChooseFile(final Rengine re, final int newFile) {
		final RList args = this.rObjectFactory.createList(new RObject[] {
				this.rObjectFactory.createVector(this.rObjectFactory.createLogiData(new boolean[] {
						(newFile == 1),
				} )),
		}, new String[] {
				"newResource",
		} );
		final RList answer = execUICommand("common/chooseFile", args, true);
		if (answer != null) {
			final RObject filenameObject = answer.get("filename");
			if (RDataUtil.isSingleString(filenameObject)) {
				return filenameObject.getData().getChar(0);
			}
		}
		return null;
	}
	
	public void rLoadHistory(final Rengine re, final String filename) {
		final RList args = this.rObjectFactory.createList(new RObject[] {
				this.rObjectFactory.createVector(this.rObjectFactory.createCharData(new String[] {
						filename,
				} )),
		}, new String[] {
				"filename",
		} );
		execUICommand("common/loadHistory", args, true);
	}
	
	public void rSaveHistory(final Rengine re, final String filename) {
		final RList args = this.rObjectFactory.createList(new RObject[] {
				this.rObjectFactory.createVector(this.rObjectFactory.createCharData(new String[] {
						filename,
				} )),
		}, new String[] {
				"filename",
		} );
		execUICommand("common/saveHistory", args, true);
	}
	
	public long rExecJCommand(final Rengine re, String commandId, final long argsExpr, final int options) {
		try {
			RList args = null;
			if (argsExpr != 0) {
				final int savedMaxDepth = this.rniMaxDepth;
				this.rniMaxDepth += 255;
				final int savedProtectedCounter = this.rniProtectedCounter;
				try {
					this.rEngine.rniProtect(argsExpr);
					this.rniProtectedCounter++;
					final RObject rObject = rniCreateDataObject(argsExpr, 0, EVAL_MODE_DEFAULT);
					if (rObject.getRObjectType() == RObject.TYPE_LIST) {
						args = (RList) rObject;
					}
				}
				finally {
					if (this.rniProtectedCounter > savedProtectedCounter) {
						this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
						this.rniProtectedCounter = savedProtectedCounter;
					}
					this.rniMaxDepth = savedMaxDepth;
				}
			}
			
			final boolean wait = ((options | 1) != 0);
			
			final String commandGroup;
			{	final int idx = commandId.indexOf(':');
				commandGroup = (idx > 0) ? commandId.substring(0, idx) : null;
				commandId = commandId.substring(idx+1);
			}
			
			RList answer = null;
			if (commandGroup.equals("ui")) {
				answer = execUICommand(commandId, args, wait);
			}
			
			if (answer != null) {
				final int savedMaxDepth = this.rniMaxDepth;
				this.rniMaxDepth += 255;
				final int savedProtectedCounter = this.rniProtectedCounter;
				try {
					return rniAssignDataObject(answer);
				}
				finally {
					if (this.rniProtectedCounter > savedProtectedCounter) {
						this.rEngine.rniUnprotect(this.rniProtectedCounter - savedProtectedCounter);
						this.rniProtectedCounter = savedProtectedCounter;
					}
					this.rniMaxDepth = savedMaxDepth;
				}
			}
		}
		catch (final Exception e) {
			LOGGER.log(Level.WARNING, "An error occurred when executing java command.", e);
		}
		
		return 0;
	}
	
	public RList execUICommand(final String command, final RList args, final boolean wait) {
		if (command == null) {
			throw new NullPointerException("command");
		}
		final MainCmdItem answer = internalMainFromR(new ExtUICmdItem(command, 0, args, wait));
		if (wait && answer instanceof ExtUICmdItem && answer.isOK()) {
			return ((ExtUICmdItem) answer).getDataArgs();
		}
		return null;
	}
	
	public void rProcessJEvents(final Rengine re) {
		while (true) {
			this.mainExchangeLock.lock();
			try {
				if (!this.hotModeRequested) {
					return;
				}
				if (this.hotMode || this.mainLoopState == ENGINE_WAIT_FOR_CLIENT
						|| this.rEngine.getMainLoopCallbacks() != JRIServer.this) {
					this.hotModeRequested = false;
					this.hotModeDelayed = true;
					return;
				}
				this.hotModeRequested = false;
				this.hotMode = true;
			}
			finally {
				this.mainExchangeLock.unlock();
			}
			final int savedSafeMode = beginSafeMode();
			try {
				this.rEngine.addMainLoopCallbacks(this.hotModeCallbacks);
				internalMainFromR(new ConsoleReadCmdItem(2, ""));
			}
			catch (final Throwable e) {
				LOGGER.log(Level.SEVERE, "An error occured when running hot mode.", e);
			}
			finally {
				endSafeMode(savedSafeMode);
				this.mainExchangeLock.lock();
				try {
					this.rEngine.addMainLoopCallbacks(JRIServer.this);
					this.hotMode = false;
					if (this.hotModeDelayed) {
						this.hotModeDelayed = false;
						this.hotModeRequested = true;
					}
					if (!this.hotModeRequested) {
						return;
					}
				}
				finally {
					this.mainExchangeLock.unlock();
				}
			}
		}
	}
	
	private void internalClearClient(final Client client) {
		final MainCmdC2SList list = new MainCmdC2SList();
		final int savedClientState = this.mainLoopClient0State;
		this.mainLoopClient0State = CLIENT_OK;
		final byte slot = client.slot;
		try {
			if (slot == 0) {
				try {
					while (this.hotMode) {
						final MainCmdItem cmdItem = this.mainLoopS2CRequest.get(this.mainLoopS2CRequest.size()-1);
						cmdItem.setAnswer(RjsStatus.CANCEL_STATUS);
						list.setObjects(cmdItem);
						if (this.mainLoopS2CNextCommandsFirst[slot] == cmdItem) {
							this.mainLoopS2CNextCommandsFirst[slot] = null;
						}
						else {
							MainCmdItem item = this.mainLoopS2CNextCommandsFirst[slot];
							while (item != null) {
								if (item.next == cmdItem) {
									item.next = null;
									break;
								}
								item = item.next;
							}
						}
						
						internalMainCallbackFromClient((byte) 0, list);
					}
				}
				catch (final Exception e) {
					LOGGER.log(Level.SEVERE, "An error occurrend when trying to cancel hot loop.", e);
				}
			}
		}
		finally {
			this.mainLoopClient0State = savedClientState;
		}
	}
	
	private void internalRStopped() {
		this.mainExchangeLock.lock();
		try {
			if (this.mainLoopState == ENGINE_STOPPED) {
				return;
			}
			this.hotMode = false;
			this.mainLoopState = ENGINE_STOPPED;
			
			this.mainExchangeClient.signalAll();
			while (this.mainLoopS2CNextCommandsFirst != null || this.mainLoopStdOutSize > 0) {
				try {
					this.mainExchangeR.awaitNanos(100000000L);
				}
				catch (final InterruptedException e) {}
				this.mainExchangeClient.signalAll();
			}
		}
		finally {
			this.mainExchangeLock.unlock();
		}
		
		final ServerRuntimePlugin[] plugins;
		synchronized (this.pluginsLock) {
			plugins = this.pluginsList;
			this.pluginsList = new ServerRuntimePlugin[0];
		}
		for (int i = 0; i < plugins.length; i++) {
			try {
				plugins[i].rjStop(V_OK);
			}
			catch (final Throwable e) {
				handlePluginError(plugins[i], e);
			}
		}
		
		synchronized (this) {
			this.serverState = S_STOPPED;
			this.rEngine = null;
		}
	}
	
	@Override
	public void onRExit() {
		internalRStopped();
		super.onRExit();
	}
	
	@Override
	public void registerGraphic(final RjsGraphic graphic) {
		this.graphics.addGraphic(graphic);
	}
	
	@Override
	public void unregisterGraphic(final RjsGraphic graphic) {
		this.graphics.removeGraphic(graphic);
	}
	
	@Override
	public MainCmdItem sendMainCmd(final MainCmdItem cmd) {
		return internalMainFromR(cmd);
	}
	
}
