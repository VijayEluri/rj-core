/*******************************************************************************
 * Copyright (c) 2008-2013 Stephan Wahlbrink (www.walware.de/goto/opensource)
 * and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.rj.server;

import java.io.IOException;

import de.walware.rj.data.RJIO;


/**
 * Command for main loop console error output.
 */
public final class ConsoleWriteErrCmdItem extends MainCmdItem {
	
	
	private final String text;
	
	
	public ConsoleWriteErrCmdItem(final String text) {
		assert (text != null);
		this.text = text;
	}
	
	/**
	 * Constructor for deserialization
	 */
	public ConsoleWriteErrCmdItem(final RJIO in) throws IOException {
		this.text = in.readString();
	}
	
	@Override
	public void writeExternal(final RJIO out) throws IOException {
		out.writeString(this.text);
	}
	
	
	@Override
	public byte getCmdType() {
		return T_CONSOLE_WRITE_ERR_ITEM;
	}
	
	@Override
	public byte getOp() {
		return 0;
	}
	
	
	@Override
	public void setAnswer(final RjsStatus status) {
		throw new UnsupportedOperationException();
	}
	
	
	@Override
	public boolean isOK() {
		return true;
	}
	
	@Override
	public RjsStatus getStatus() {
		return null;
	}
	
	@Override
	public String getDataText() {
		return this.text;
	}
	
	
	@Override
	public boolean testEquals(final MainCmdItem other) {
		if (!(other instanceof ConsoleWriteErrCmdItem)) {
			return false;
		}
		final ConsoleWriteErrCmdItem otherItem = (ConsoleWriteErrCmdItem) other;
		if (this.options != otherItem.options) {
			return false;
		}
		return this.text.equals(otherItem.getDataText());
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(128);
		sb.append("ConsoleWriteErrCmdItem");
		sb.append("\n\t").append("options= 0x").append(Integer.toHexString(this.options));
		sb.append("\n<TEXT>\n");
		sb.append(this.text);
		sb.append("\n</TEXT>");
		return sb.toString();
	}
	
}
