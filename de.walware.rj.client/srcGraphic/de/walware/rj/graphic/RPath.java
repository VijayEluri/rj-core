/*=============================================================================#
 # Copyright (c) 2011-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.rj.graphic;


/**
 * Path with vertices <code>(x[1], y[1])</code>, ..., <code>(x[n-1], y[n-1])</code>.
 */
public class RPath extends RGraphicElement {
	
	
	/**
	 * Number of vertices per segment
	 */
	public final int[] n;
	
	/**
	 * Coordinates of the vertices
	 */
	public final double[] x, y;
	
	public final int mode;
	
	
	/**
	 * Creates a new path
	 * 
	 * @param n {@link #n}
	 * @param x {@link #x}
	 * @param y {@link #y}
	 * @winding {@link #mode}
	 */
	public RPath(final int[] n, final double[] x, final double[] y, final int mode) {
		this.n = n;
		this.x = x;
		this.y = y;
		this.mode = mode;
	}
	
	
	@Override
	public final byte getInstructionType() {
		return DRAW_PATH;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		final int n = this.x.length;
		if (n == 0) {
			return "RPath[]";
		}
		final StringBuilder sb = new StringBuilder(14 + this.x.length*20);
		sb.append("RPath[(");
		sb.append(this.x[0]);
		sb.append(",");
		sb.append(this.y[0]);
		for (int i = 1; i < n; i++) {
			sb.append("), (");
			sb.append(this.x[i]);
			sb.append(",");
			sb.append(this.y[i]);
		}
		sb.append(")]");
		return sb.toString();
	}
	
}
