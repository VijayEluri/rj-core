/*******************************************************************************
 * Copyright (c) 2009 Stephan Wahlbrink and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.rj.server.jriImpl;

import java.io.IOException;
import java.io.ObjectInput;

import de.walware.rj.data.defaultImpl.RCharacterDataImpl;


public class JRICharacterDataImpl extends RCharacterDataImpl {
	
	
	public JRICharacterDataImpl(final String[] values) {
		super(values);
	}
	
	public JRICharacterDataImpl(final ObjectInput in) throws IOException, ClassNotFoundException {
		super(in);
	}
	
	
	public String[] getJRIValueArray() {
		if (this.charValues.length == this.length) {
			return this.charValues;
		}
		final String[] array = new String[this.length];
		System.arraycopy(this.charValues, 0, array, 0, this.length);
		return array;
	}
	
}
