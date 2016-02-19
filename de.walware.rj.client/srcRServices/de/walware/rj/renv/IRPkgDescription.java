/*=============================================================================#
 # Copyright (c) 2010-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.rj.renv;


/**
 * R package description.
 * 
 * @since de.walware.rj.renv 2.0
 */
public interface IRPkgDescription extends IRPkg {
	
	
	String getTitle();
	
	String getDescription();
	
	String getAuthor();
	
	String getMaintainer();
	
	String getUrl();
	
	String getBuilt();
	
}
