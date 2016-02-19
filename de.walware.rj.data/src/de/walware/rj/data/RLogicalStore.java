/*=============================================================================#
 # Copyright (c) 2009-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the GNU Lesser General Public License
 # v2.1 or newer, which accompanies this distribution, and is available at
 # http://www.gnu.org/licenses/lgpl.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.rj.data;


/**
 * Interface for R data stores of type {@link RStore#LOGICAL}.
 * <p>
 * An R data store implements this interface if the R function
 * <code>typeof(object)</code> returns 'logical'.</p>
 */
public interface RLogicalStore extends RStore<Boolean> {
	
	
	@Override
	Boolean get(int idx);
	@Override
	Boolean get(long idx);
	
	@Override
	Boolean[] toArray();
	
}
