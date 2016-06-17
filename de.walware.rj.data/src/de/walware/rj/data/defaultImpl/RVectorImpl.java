/*=============================================================================#
 # Copyright (c) 2009-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of either (per the licensee's choosing)
 #   - the Eclipse Public License v1.0
 #     which accompanies this distribution, and is available at
 #     http://www.eclipse.org/legal/epl-v10.html, or
 #   - the GNU Lesser General Public License v2.1 or newer
 #     which accompanies this distribution, and is available at
 #     http://www.gnu.org/licenses/lgpl.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.rj.data.defaultImpl;

import java.io.IOException;

import de.walware.rj.data.RJIO;
import de.walware.rj.data.RList;
import de.walware.rj.data.RObjectFactory;
import de.walware.rj.data.RStore;
import de.walware.rj.data.RVector;


public class RVectorImpl<TData extends RStore<?>> extends AbstractRObject
		implements RVector<TData>, ExternalizableRObject {
	
	
	private TData data;
	private long length;
	
	private String className1;
	private RStore<?> namesAttribute;
	
	
	public RVectorImpl(final TData data, final String className1) {
		this(data, data.getLength(), className1, null);
	}
	
	public RVectorImpl(final TData data) {
		this(data, data.getLength(), data.getBaseVectorRClassName(), null);
	}
	
	public RVectorImpl(final TData data, final String className1, final String[] initialNames) {
		this(data, data.getLength(), className1, initialNames);
	}
	
	public RVectorImpl(final TData data, final long length, final String className1, final String[] initialNames) {
		if (data == null || className1 == null) {
			throw new NullPointerException();
		}
		if ((initialNames != null && initialNames.length != length)
				|| (data.getLength() >= 0 && data.getLength() != length) ) {
			throw new IllegalArgumentException();
		}
		this.data = data;
		this.length = length;
		this.className1 = className1;
		if (initialNames != null) {
			this.namesAttribute = new RCharacterDataImpl(initialNames);
		}
	}
	
	public RVectorImpl(final TData data, final String className1, final RStore<?> initialNames) {
		if (data == null || className1 == null) {
			throw new NullPointerException();
		}
		if ((initialNames != null && initialNames.getLength() != data.getLength())) {
			throw new IllegalArgumentException();
		}
		this.data = data;
		this.length = data.getLength();
		this.className1 = className1;
		this.namesAttribute = initialNames;
	}
	
	public RVectorImpl(final RJIO io, final RObjectFactory factory) throws IOException {
		readExternal(io, factory);
	}
	
	public void readExternal(final RJIO io, final RObjectFactory factory) throws IOException {
		//-- options
		final int options = io.readInt();
		//-- special attributes
		if ((options & RObjectFactory.O_CLASS_NAME) != 0) {
			this.className1 = io.readString();
		}
		this.length = io.readVULong((byte) (options & RObjectFactory.O_LENGTHGRADE_MASK));
		if ((options & RObjectFactory.O_WITH_NAMES) != 0) {
			this.namesAttribute = factory.readNames(io, this.length);
		}
		//-- data
		this.data = (TData) factory.readStore(io, this.length);
		if ((options & RObjectFactory.O_CLASS_NAME) == 0) {
			this.className1 = this.data.getBaseVectorRClassName();
		}
		// attributes
		if ((options & RObjectFactory.O_WITH_ATTR) != 0) {
			setAttributes(factory.readAttributeList(io));
		}
	}
	
	@Override
	public void writeExternal(final RJIO io, final RObjectFactory factory) throws IOException {
		//-- options
		int options = io.getVULongGrade(this.length);
		if (!this.className1.equals(this.data.getBaseVectorRClassName())) {
			options |= RObjectFactory.O_CLASS_NAME;
		}
		if ((io.flags & RObjectFactory.F_ONLY_STRUCT) == 0 && this.namesAttribute != null) {
			options |= RObjectFactory.O_WITH_NAMES;
		}
		final RList attributes = ((io.flags & RObjectFactory.F_WITH_ATTR) != 0) ? getAttributes() : null;
		if (attributes != null) {
			options |= RObjectFactory.O_WITH_ATTR;
		}
		io.writeInt(options);
		//-- special attributes
		if ((options & RObjectFactory.O_CLASS_NAME) != 0) {
			io.writeString(this.className1);
		}
		io.writeVULong((byte) (options & RObjectFactory.O_LENGTHGRADE_MASK), this.length);
		if ((options & RObjectFactory.O_WITH_NAMES) != 0) {
			factory.writeNames(this.namesAttribute, io);
		}
		//-- data
		factory.writeStore(this.data, io);
		// attributes
		if ((options & RObjectFactory.O_WITH_ATTR) != 0) {
			factory.writeAttributeList(attributes, io);
		}
	}
	
	
	@Override
	public byte getRObjectType() {
		return TYPE_VECTOR;
	}
	
	@Override
	public String getRClassName() {
		return this.className1;
	}
	
	@Override
	public long getLength() {
		return this.length;
	}
	
	@Override
	public RStore<?> getNames() {
		return this.namesAttribute;
	}
	
	
	@Override
	public TData getData() {
		return this.data;
	}
	
	
	public void setData(final TData data) {
		this.data = data;
	}
	
	public void insert(final int idx) {
		((RDataResizeExtension<?>) this.data).insertNA(idx);
		this.length++;
	}
	
	public void remove(final int idx) {
		((RDataResizeExtension<?>) this.data).remove(idx);
		this.length--;
	}
	
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("RObject type=RVector, class=").append(getRClassName());
		sb.append("\n\tlength=").append(getLength());
		sb.append("\n\tdata: ");
		sb.append(this.data.toString());
		return sb.toString();
	}
	
}
