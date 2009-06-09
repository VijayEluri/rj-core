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

package de.walware.rj.data.defaultImpl;

import de.walware.rj.data.RObject;
import de.walware.rj.data.RRawStore;
import de.walware.rj.data.RStore;


public abstract class AbstractRawData extends AbstractRData
		implements RRawStore {
	
	
	public final int getStoreType() {
		return RStore.RAW;
	}
	
	public final String getBaseVectorRClassName() {
		return RObject.CLASSNAME_RAW;
	}
	
	
	public Byte[] toArray() {
		final Byte[] array = new Byte[this.length];
		for (int i = 0; i < this.length; i++) {
			array[i] = Byte.valueOf(getRaw(i));
		}
		return array;
	}
	
}