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

package de.walware.rj.server;

import java.io.IOException;
import java.util.Arrays;

import de.walware.rj.data.RJIO;


/**
 * Command item for GD.
 */
public abstract class GDCmdItem extends MainCmdItem {
	
	
	public static final byte SET_CLIP =                     0x01;
	public static final byte SET_COLOR =                    0x02;
	public static final byte SET_FILL =                     0x03;
	public static final byte SET_LINE =                     0x04;
	public static final byte SET_FONT =                     0x05;
	
	public static final byte DRAW_LINE =                    0x11;
	public static final byte DRAW_RECTANGLE =               0x12;
	public static final byte DRAW_POLYLINE =                0x13;
	public static final byte DRAW_POLYGON =                 0x14;
	public static final byte DRAW_PATH =                    0x15;
	public static final byte DRAW_CIRCLE =                  0x16;
	public static final byte DRAW_TEXT =                    0x17;
	public static final byte DRAW_RASTER =                  0x18;
	
	public static final byte CAPTURE =                      0x1c;
	
	public static final byte C_NEW_PAGE =                   0x21;
	public static final byte C_CLOSE_DEVICE =               0x22;
	public static final byte C_GET_SIZE =                   0x23;
	public static final byte C_SET_ACTIVE_OFF =             0x24;
	public static final byte C_SET_ACTIVE_ON =              0x25;
	public static final byte C_SET_MODE =                   0x26;
	public static final byte C_GET_FONTMETRIC =             0x27;
	public static final byte C_GET_STRINGWIDTH =            0x28;
	
	public static final byte U_LOCATOR =                    0x31;
	
	
	private static final double[] NO_DATA = new double[0];
	
	
	public static final class Answer extends GDCmdItem {
		
		
		private static final int OM_WITHDATA =              0x07000000;
		private static final int OV_WITHDOUBLE =            0x01000000;
		private static final int OV_WITHBYTE =              0x02000000;
		
		private final Object data;
		
// use DoubleAnswer
//		public Answer(final byte requestId, final int devId, final double[] data) {
//			this.options = (data != null) ? (OV_WITHDOUBLE | OV_WAITFORCLIENT) : OV_WAITFORCLIENT;
//			this.devId = devId;
//			this.data = data;
//			this.requestId = requestId;
//		}
		
		public Answer(final byte requestId, final int devId, final byte[] data) {
			this.options = (data != null) ? (OV_WITHBYTE | OV_WAITFORCLIENT) : OV_WAITFORCLIENT;
			this.devId = devId;
			this.data = data;
			this.requestId = requestId;
		}
		
		public Answer(final byte requestId, final int devId, final RjsStatus status) {
			this.options = OV_WAITFORCLIENT | (status.getSeverity() << OS_STATUS);
			this.devId = devId;
			this.data = NO_DATA;
			this.requestId = requestId;
		}
		
		/**
		 * Constructor for deserialization
		 */
		public Answer(final RJIO io) throws IOException, ClassNotFoundException {
			this.options = io.readInt();
			this.devId = io.readInt();
			this.requestId = io.readByte();
			switch (this.options & OM_WITHDATA) {
			case OV_WITHDOUBLE: {
				final double[] d = new double[io.readInt()];
				io.readDoubleData(d, d.length);
				this.data = d;
				break; }
			case OV_WITHBYTE: {
				final byte[] b = new byte[io.readInt()];
				io.readByteData(b, b.length);
				this.data = b;
				break; }
			default:
				this.data = null;
				break;
			}
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.options);
			io.writeInt(this.devId);
			io.writeByte(this.requestId);
			switch (this.options & OM_WITHDATA) {
			case OV_WITHDOUBLE: {
				final double[] d = (double[]) this.data;
				io.writeInt(d.length);
				io.writeDoubleData(d, d.length);
				break; }
			case OV_WITHBYTE: {
				final byte[] b = (byte[]) this.data;
				io.writeInt(b.length);
				io.writeByteData(b, b.length);
				break; }
			}
		}
		
		
		@Override
		public double[] getDoubleData() {
			return (double[]) this.data;
		}
		
		@Override
		public byte[] getByteData() {
			return (byte[]) this.data;
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer(100);
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=ANSWER");
			sb.append(")");
			sb.append("\n<GD-DATA>\n");
			switch (this.options & OM_WITHDATA) {
			case OV_WITHDOUBLE:
				sb.append(Arrays.toString((double[]) this.data));
			}
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DoubleAnswer extends GDCmdItem {
		
		
		private final double[] data;
		
		
		public DoubleAnswer(final byte requestId, final int devId, final double[] data) {
			this.options = (data != null) ? (Answer.OV_WITHDOUBLE | OV_WAITFORCLIENT) : OV_WAITFORCLIENT;
			this.devId = devId;
			this.data = data;
			this.requestId = requestId;
		}
		
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.options);
			io.writeInt(this.devId);
			io.writeByte(this.requestId);
			if (this.data != null) {
				final int length = this.data.length;
				io.writeInt(length);
				io.writeDoubleData(this.data, length);
			}
		}
		
		
		@Override
		public double[] getDoubleData() {
			return this.data;
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer(100);
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=ANSWER");
			sb.append(")");
			sb.append("\n<GD-DATA>\n");
			sb.append(Arrays.toString(this.data));
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class CInit extends GDCmdItem {
		
		
		private final double w;
		private final double h;
		private final int canvasColor;
		private final boolean active;
		
		
		public CInit(final int devId, final double w, final double h, final int canvasColor,
				final boolean activate, final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.w = w;
			this.h = h;
			this.canvasColor = canvasColor;
			this.active = activate;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(C_NEW_PAGE);
			io.writeDouble(this.w);
			io.writeDouble(this.h);
			io.writeInt(this.canvasColor);
			io.writeBoolean(this.active);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_NEW_PAGE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class CCloseDevice extends GDCmdItem {
		
		
		public CCloseDevice(final int devId, final byte slot) {
			this.options = 0;
			this.devId = devId;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(C_CLOSE_DEVICE);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_CLOSE_DEVICE);
			sb.append(")\n<GD-DATA />");
			return sb.toString();
		}
		
	}
	
	public static final class CGetSize extends GDCmdItem {
		
		
		public CGetSize(final int devId, final byte slot) {
			this.options = OV_WAITFORCLIENT;
			this.devId = devId;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt((OV_WAITFORCLIENT | this.devId));
			io.writeByte(C_GET_SIZE);
			io.writeByte(this.requestId);
		}
		
		
		@Override
		public void setAnswer(final RjsStatus status) {
			this.options = (this.options & OM_CLEARFORANSWER) | (status.getSeverity() << OS_STATUS);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_GET_SIZE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class CSetActiveOff extends GDCmdItem {
		
		
		public CSetActiveOff(final int devId, final byte slot) {
			this.options = 0;
			this.devId = devId;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(C_SET_ACTIVE_OFF);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_SET_ACTIVE_OFF);
			sb.append(")\n<GD-DATA />");
			return sb.toString();
		}
		
	}
	
	public static final class CSetActiveOn extends GDCmdItem {
		
		
		public CSetActiveOn(final int devId, final byte slot) {
			this.options = 0;
			this.devId = devId;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(C_SET_ACTIVE_ON);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_SET_ACTIVE_ON);
			sb.append(")\n<GD-DATA />");
			return sb.toString();
		}
		
	}
	
	public static final class CSetMode extends GDCmdItem {
		
		
		private final byte mode;
		
		
		public CSetMode(final int devId, final int mode, final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.mode = (byte) mode;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(C_SET_MODE);
			io.writeByte(this.mode);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_SET_MODE);
			sb.append(")<GD-DATA>\nmode = ");
			sb.append(this.mode);
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class CGetFontMetric extends GDCmdItem {
		
		
		private final int c;
		
		
		public CGetFontMetric(final int devId, final int c, final byte slot) {
			this.options = OV_WAITFORCLIENT;
			this.devId = devId;
			this.c = c;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt((OV_WAITFORCLIENT | this.devId));
			io.writeByte(C_GET_FONTMETRIC);
			io.writeByte(this.requestId);
			io.writeInt(this.c);
		}
		
		
		@Override
		public void setAnswer(final RjsStatus status) {
			this.options = (this.options & OM_CLEARFORANSWER) | (status.getSeverity() << OS_STATUS);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_GET_FONTMETRIC);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class CGetStrWidth extends GDCmdItem {
		
		
		private final String text;
		
		
		public CGetStrWidth(final int devId, final String text, final byte slot) {
			this.options = OV_WAITFORCLIENT;
			this.devId = devId;
			this.text = text;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt((OV_WAITFORCLIENT | this.devId));
			io.writeByte(C_GET_STRINGWIDTH);
			io.writeByte(this.requestId);
			io.writeString(this.text);
		}
		
		
		@Override
		public void setAnswer(final RjsStatus status) {
			this.options = (this.options & OM_CLEARFORANSWER) | (status.getSeverity() << OS_STATUS);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(C_GET_STRINGWIDTH);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class SetClip extends GDCmdItem {
		
		
		private final double x0;
		private final double y0;
		private final double x1;
		private final double y1;
		
		
		public SetClip(final int devId, final double x0, final double y0, final double x1, final double y1,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(SET_CLIP);
			io.writeDouble(this.x0);
			io.writeDouble(this.y0);
			io.writeDouble(this.x1);
			io.writeDouble(this.y1);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(SET_CLIP);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class SetColor extends GDCmdItem {
		
		
		private final int cc;
		
		
		public SetColor(final int devId, final int cc, final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.cc = cc;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(SET_COLOR);
			io.writeInt(this.cc);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(SET_COLOR);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class SetFill extends GDCmdItem {
		
		
		private final int cc;
		
		
		public SetFill(final int devId, final int cc, final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.cc = cc;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(SET_FILL);
			io.writeInt(this.cc);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(SET_FILL);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class SetLine extends GDCmdItem {
		
		
		private final int type;
		private final float width;
		private final byte cap;
		private final byte join;
		private final float joinMiterLimit;
		
		
		public SetLine(final int devId, final int type, final float width,
				final byte cap, final byte join, final float joinMiterLimit,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.type= type;
			this.width= width;
			this.cap= cap;
			this.join= join;
			this.joinMiterLimit= joinMiterLimit;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(SET_LINE);
			io.writeInt(this.type);
			io.writeFloat(this.width);
			io.writeByte(this.cap);
			io.writeByte(this.join);
			io.writeFloat(this.joinMiterLimit);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(SET_LINE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class SetFont extends GDCmdItem {
		
		
		private final String family;
		private final int face;
		private final float pointSize;
		private final float lineHeight;
		
		
		public SetFont(final int devId, final String family, final int face, final float pointSize,
				final float lineHeight,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.family= family;
			this.face= face;
			this.pointSize= pointSize;
			this.lineHeight= lineHeight;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(SET_FONT);
			io.writeString(this.family);
			io.writeInt(this.face);
			io.writeFloat(this.pointSize);
			io.writeFloat(this.lineHeight);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(SET_FONT);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawLine extends GDCmdItem {
		
		
		private final double x0;
		private final double y0;
		private final double x1;
		private final double y1;
		
		
		public DrawLine(final int devId, final double x0, final double y0, final double x1, final double y1,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_LINE);
			io.writeDouble(this.x0);
			io.writeDouble(this.y0);
			io.writeDouble(this.x1);
			io.writeDouble(this.y1);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_LINE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawRect extends GDCmdItem {
		
		
		private final double x0;
		private final double y0;
		private final double x1;
		private final double y1;
		
		
		public DrawRect(final int devId, final double x0, final double y0, final double x1, final double y1,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_RECTANGLE);
			io.writeDouble(this.x0);
			io.writeDouble(this.y0);
			io.writeDouble(this.x1);
			io.writeDouble(this.y1);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_RECTANGLE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawPolyline extends GDCmdItem {
		
		
		private final double x[];
		private final double y[];
		
		
		public DrawPolyline(final int devId, final double x[], final double y[],
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x = x;
			this.y = y;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_POLYLINE);
			final int length = this.x.length;
			io.writeInt(length);
			io.writeDoubleData(this.x, length);
			io.writeDoubleData(this.y, length);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_POLYLINE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawPolygon extends GDCmdItem {
		
		
		private final double x[];
		private final double y[];
		
		
		public DrawPolygon(final int devId, final double x[], final double y[],
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x = x;
			this.y = y;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_POLYGON);
			final int length = this.x.length;
			io.writeInt(length);
			io.writeDoubleData(this.x, length);
			io.writeDoubleData(this.y, length);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_POLYGON);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawPath extends GDCmdItem {
		
		
		private final int[] n;
		private final double x[];
		private final double y[];
		private final int mode;
		
		
		public DrawPath(final int devId, final int[] n, final double x[], final double y[],
				final int mode, final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.n = n;
			this.x = x;
			this.y = y;
			this.mode = mode;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_PATH);
			io.writeIntArray(this.n, this.n.length);
			final int length = this.x.length;
			io.writeInt(length);
			io.writeDoubleData(this.x, length);
			io.writeDoubleData(this.y, length);
			io.writeInt(this.mode);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_PATH);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawCircle extends GDCmdItem {
		
		
		private final double x;
		private final double y;
		private final double r;
		
		
		public DrawCircle(final int devId, final double x, final double y, final double r,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			this.x = x;
			this.y = y;
			this.r = r;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_CIRCLE);
			io.writeDouble(this.x);
			io.writeDouble(this.y);
			io.writeDouble(this.r);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_CIRCLE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawText extends GDCmdItem {
		
		
		private final String text;
		private final double x, y;
		private final double rDeg;
		private final double hAdj;
		
		
		public DrawText(final int devId, final String text,
				final double x, final double y, final double rDeg, final double hAdj, 
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			
			this.text = text;
			this.x = x;
			this.y = y;
			this.rDeg = rDeg;
			this.hAdj = hAdj;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_TEXT);
			io.writeString(this.text);
			io.writeDouble(this.x);
			io.writeDouble(this.y);
			io.writeDouble(this.rDeg);
			io.writeDouble(this.hAdj);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_TEXT);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class DrawRaster extends GDCmdItem {
		
		
		private final byte[] imgData;
		private final boolean imgAlpha;
		private final int imgW, imgH;
		private final double x, y;
		private final double w, h;
		private final double rDeg;
		private final boolean interpolate;
		
		
		public DrawRaster(final int devId,
				final byte[] imgData, final boolean imgAlpha, final int imgW, final int imgH,
				final double x, final double y, final double w, final double h,
				final double rDeg, final boolean interpolate,
				final byte slot) {
			this.options = 0;
			this.devId = devId;
			
			this.imgData = imgData;
			this.imgAlpha = imgAlpha;
			this.imgW = imgW;
			this.imgH = imgH;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.rDeg = rDeg;
			this.interpolate = interpolate;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(DRAW_RASTER);
			io.writeInt(this.imgData.length);
			io.writeByteData(this.imgData, this.imgData.length);
			io.writeBoolean(this.imgAlpha);
			io.writeInt(this.imgW);
			io.writeInt(this.imgH);
			io.writeDouble(this.x);
			io.writeDouble(this.y);
			io.writeDouble(this.w);
			io.writeDouble(this.h);
			io.writeDouble(this.rDeg);
			io.writeBoolean(this.interpolate);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(DRAW_RASTER);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class Capture extends GDCmdItem {
		
		
		private final int w, h;
		
		
		public Capture(final int devId,
				final int w, final int h,
				final byte slot) {
			this.options = OV_WAITFORCLIENT;
			this.devId = devId;
			
			this.w = w;
			this.h = h;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt(this.devId);
			io.writeByte(CAPTURE);
			io.writeByte(this.requestId);
			io.writeInt(this.w);
			io.writeInt(this.h);
		}
		
		
		@Override
		public void setAnswer(final RjsStatus status) {
			this.options = (this.options & OM_CLEARFORANSWER) | (status.getSeverity() << OS_STATUS);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(CAPTURE);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	public static final class Locator extends GDCmdItem {
		
		
		public Locator(final int devId,
				final byte slot) {
			this.options = OV_WAITFORCLIENT;
			this.devId = devId;
			
			this.slot = slot;
		}
		
		@Override
		public void writeExternal(final RJIO io) throws IOException {
			io.writeInt((OV_WAITFORCLIENT | this.devId));
			io.writeByte(U_LOCATOR);
			io.writeByte(this.requestId);
		}
		
		
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer();
			sb.append("GDCmdItem (options=0x");
			sb.append(Integer.toHexString(this.options));
			sb.append(", device=");
			sb.append(this.devId);
			sb.append(", commandId=");
			sb.append(U_LOCATOR);
			sb.append(")\n<GD-DATA>\n");
			sb.append("\n</GD-DATA>");
			return sb.toString();
		}
		
	}
	
	
	protected int devId;
	
	
	/**
	 * Constructor for automatic deserialization
	 */
	protected GDCmdItem() {
	}
	
	
	@Override
	public final byte getCmdType() {
		return T_GRAPH_ITEM;
	}
	
	@Override
	public byte getOp() {
		return 0;
	}
	
	@Override
	public final boolean isOK() {
		return ((this.options & OM_STATUS) == RjsStatus.OK);
	}
	
	@Override
	public final RjsStatus getStatus() {
		return null;
	}
	
	public final int getDeviceId() {
		return this.devId;
	}
	
	@Override
	public void setAnswer(final RjsStatus status) {
		throw new UnsupportedOperationException();
	}
	
	
	public double[] getDoubleData() {
		throw new UnsupportedOperationException();
	}
	
	public byte[] getByteData() {
		throw new UnsupportedOperationException();
	}
	
	
	@Override
	public final String getDataText() {
		return null;
	}
	
	
	@Override
	public boolean testEquals(final MainCmdItem other) {
		if (!(other instanceof GDCmdItem)) {
			return false;
		}
		final GDCmdItem otherItem = (GDCmdItem) other;
		if (this.options != otherItem.options) {
			return false;
		}
		if (getDeviceId() != otherItem.getDeviceId()) {
			return false;
		}
		return true;
	}
	
}
