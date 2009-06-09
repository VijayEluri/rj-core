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

package de.walware.rj.data;

import java.util.List;


/**
 * Data store for 'factor' data
 */
public interface RFactorStore extends RIntegerStore {
	
	boolean isOrdered();
	
	List<String> getLevels();
	int getLevelCount();
	
	void addLevel(final String label);
	void renameLevel(final String oldLabel, final String newLabel);
	void insertLevel(final int position, final String label);
	void removeLevel(final String label);
	
	RCharacterStore toCharacterData();
	public Integer[] toArray();
	
}