/*******************************************************************************
 * Copyright (c) 2008-2010 Stephan Wahlbrink (www.walware.de/goto/opensource)
 * and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.rj.server.jriImpl;

import java.io.IOException;

import de.walware.rj.data.RJIO;
import de.walware.rj.data.RObject;
import de.walware.rj.data.RStore;
import de.walware.rj.data.defaultImpl.REnvironmentImpl;
import de.walware.rj.data.defaultImpl.RFactorDataStruct;
import de.walware.rj.data.defaultImpl.RFunctionImpl;
import de.walware.rj.data.defaultImpl.RMissing;
import de.walware.rj.data.defaultImpl.RNull;
import de.walware.rj.data.defaultImpl.RObjectFactoryImpl;
import de.walware.rj.data.defaultImpl.ROtherImpl;
import de.walware.rj.data.defaultImpl.RReferenceImpl;
import de.walware.rj.data.defaultImpl.RS4ObjectImpl;
import de.walware.rj.data.defaultImpl.RVectorImpl;

public class JRIObjectFactory extends RObjectFactoryImpl {
	
	
	@Override
	public RObject readObject(final RJIO io) throws IOException {
		final byte type = io.in.readByte();
		switch (type) {
		case -1:
			return null;
		case RObject.TYPE_NULL:
			return RNull.INSTANCE;
		case RObject.TYPE_VECTOR: {
			return new RVectorImpl(io, this); }
		case RObject.TYPE_ARRAY:
			return new JRIArrayImpl(io, this);
		case RObject.TYPE_LIST:
			return new JRIListImpl(io, this);
		case RObject.TYPE_DATAFRAME:
			return new JRIDataFrameImpl(io, this);
		case RObject.TYPE_ENV:
			return new REnvironmentImpl(io, this);
		case RObject.TYPE_FUNCTION:
			return new RFunctionImpl(io, this);
		case RObject.TYPE_REFERENCE:
			return new RReferenceImpl(io, this);
		case RObject.TYPE_S4OBJECT:
			return new RS4ObjectImpl(io, this);
		case RObject.TYPE_OTHER:
			return new ROtherImpl(io, this);
		case RObject.TYPE_MISSING:
			return RMissing.INSTANCE;
		default:
			throw new IOException("object type = " + type);
		}
	}
	
	@Override
	public RStore readStore(final RJIO io) throws IOException {
		if ((io.flags & F_ONLY_STRUCT) == 0) {
			final byte storeType = io.in.readByte();
			switch (storeType) {
			case RStore.LOGICAL:
				return new JRILogicalDataImpl(io);
			case RStore.INTEGER:
				return new JRIIntegerDataImpl(io);
			case RStore.NUMERIC:
				return new JRINumericDataImpl(io);
			case RStore.COMPLEX:
				return new JRIComplexDataImpl(io);
			case RStore.CHARACTER:
				return new JRICharacterDataImpl(io);
			case RStore.RAW:
				return new JRIRawDataImpl(io);
			case RStore.FACTOR:
				return new JRIFactorDataImpl(io);
			default:
				throw new IOException("store type = " + storeType);
			}
		}
		else {
			final byte storeType = io.in.readByte();
			switch (storeType) {
			case RStore.LOGICAL:
				return LOGI_STRUCT_DUMMY;
			case RStore.INTEGER:
				return INT_STRUCT_DUMMY;
			case RStore.NUMERIC:
				return NUM_STRUCT_DUMMY;
			case RStore.COMPLEX:
				return CPLX_STRUCT_DUMMY;
			case RStore.CHARACTER:
				return CHR_STRUCT_DUMMY;
			case RStore.RAW:
				return RAW_STRUCT_DUMMY;
			case RStore.FACTOR:
				return new RFactorDataStruct(io.in.readBoolean(), io.in.readInt());
			default:
				throw new IOException("store type = " + storeType);
			}
		}
	}
	
	@Override
	public RStore readNames(final RJIO io) throws IOException {
		final byte type = io.in.readByte();
		if (type == RStore.CHARACTER) {
			return new JRICharacterDataImpl(io);
		}
		if (type == 0) {
			return null;
		}
		throw new IOException();
	}
	
}
