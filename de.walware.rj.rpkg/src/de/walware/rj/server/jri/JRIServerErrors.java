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

import java.util.logging.Logger;


public final class JRIServerErrors {
	
	
	public static final int CODE_CTRL_COMMON = 0x2000;
	public static final int CODE_CTRL_REQUEST_CANCEL = 0x2110;
	public static final int CODE_CTRL_REQUEST_HOT_MODE = 0x2120;
	
	public static final int CODE_DATA_COMMON = 0x1000;
	public static final int CODE_DATA_EVAL_DATA = 0x1010;
	public static final int CODE_DATA_RESOLVE_DATA = 0x1020;
	public static final int CODE_DATA_ASSIGN_DATA = 0x1030;
	
	
	public static final Logger LOGGER = Logger.getLogger("de.walware.rj.server.jri");
	
}
