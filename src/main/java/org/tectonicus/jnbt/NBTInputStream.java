package org.tectonicus.jnbt;

//@formatter:off

/*
* JNBT License
* 
* Copyright (c) 2010 Graham Edgecombe
* All rights reserved.
* 
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
* 
*     * Redistributions of source code must retain the above copyright notice,
*       this list of conditions and the following disclaimer.
*       
*     * Redistributions in binary form must reproduce the above copyright
*       notice, this list of conditions and the following disclaimer in the
*       documentation and/or other materials provided with the distribution.
*       
*     * Neither the name of the JNBT team nor the names of its
*       contributors may be used to endorse or promote products derived from
*       this software without specific prior written permission.
* 
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE. 
*/

//@formatter:on

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * <p>
 * This class reads <strong>NBT</strong>, or <strong>Named Binary Tag</strong>
 * streams, and produces an object graph of subclasses of the <code>Tag</code>
 * object.
 * </p>
 * 
 * <p>
 * The NBT format was created by Markus Persson. It is described at
 * <a href="https://minecraft.wiki/w/NBT_format">https://minecraft.wiki/w/NBT_format</a>.
 * </p>
 * 
 * @author Graham Edgecombe
 * 
 */
public final class NBTInputStream implements Closeable {
/**
	 * The data input stream.
	 */
	private final DataInputStream is;
	private final NBTReadLimits limits;
	
	/**
	 * Creates a new <code>NBTInputStream</code>, which will source its data
	 * from the specified input stream.
	 * 
	 * @param is
	 *            The input stream.
	 * @param gzipped
	 *            Whether the stream is GZip-compressed.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public NBTInputStream(InputStream is, final boolean gzipped) throws IOException {
		this(is, gzipped ? NBTCompression.GZIP : NBTCompression.NONE, NBTReadLimits.DEFAULT);
	}
	
	/**
	 * Creates a new <code>NBTInputStream</code>, which will source its data
	 * from the specified GZIP-compressed input stream.
	 * 
	 * @param is
	 *            The input stream.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public NBTInputStream(final InputStream is) throws IOException {
		this(is, NBTCompression.GZIP, NBTReadLimits.DEFAULT);
	}
	
	/**
	 * Creates a new <code>NBTInputStream</code> that reads from an existing
	 * data input stream as-is, applying no decompression, with the
	 * {@linkplain NBTReadLimits#DEFAULT default read limits}.
	 *
	 * @param is
	 *            The data input stream.
	 */
	public NBTInputStream(final DataInputStream is) {
		this(is, NBTReadLimits.DEFAULT);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that reads from an existing
	 * data input stream as-is, applying no decompression.
	 *
	 * @param is
	 *            The data input stream.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 */
	public NBTInputStream(final DataInputStream is, final NBTReadLimits limits) {
		if (is == null || limits == null) {
			throw new NullPointerException("Input and limits are required.");
		}
		this.is = is;
		this.limits = limits;
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that reads from the specified
	 * input stream with the given compression and the
	 * {@linkplain NBTReadLimits#DEFAULT default read limits}.
	 *
	 * @param is
	 *            The input stream.
	 * @param compression
	 *            The compression applied to the stream.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public NBTInputStream(InputStream is, NBTCompression compression) throws IOException {
		this(is, compression, NBTReadLimits.DEFAULT);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that reads from the specified
	 * input stream with the given compression and read limits.
	 *
	 * @param input
	 *            The input stream.
	 * @param compression
	 *            The compression applied to the stream.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 * @throws IOException
	 *             if an I/O error occurs, such as a missing or corrupt GZip
	 *             header. The input stream is closed before this is thrown.
	 */
	public NBTInputStream(InputStream input, NBTCompression compression,
			NBTReadLimits limits) throws IOException {
		if (input == null || compression == null || limits == null) {
			throw new NullPointerException("Input, compression and limits are required.");
		}
		try {
			if (compression == NBTCompression.GZIP) {
				input = new GZIPInputStream(input);
			} else if (compression == NBTCompression.DEFLATE) {
				input = new InflaterInputStream(input);
			}
		} catch (IOException | RuntimeException e) {
			// The stream would otherwise leak: the caller never gets an object to close.
			try {
				input.close();
			} catch (IOException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw e;
		}
		this.is = new DataInputStream(input);
		this.limits = limits;
	}

	/**
	 * Reads an NBT tag from the stream.
	 * <p>
	 * If a compound contains the same name more than once, the last entry
	 * wins and keeps the position of the first, as in Minecraft.
	 * </p>
	 *
	 * @return The tag that was read.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public Tag readTag() throws IOException {
	
		return readTag(0);
	}
	
	/**
	 * Reads an NBT from the stream.
	 * 
	 * @param depth
	 *            The depth of this tag.
	 * @return The tag that was read.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	private Tag readTag(final int depth) throws IOException {
	
		final int type = is.readByte() & 0xFF;
		if (type != NBTConstants.TYPE_END) {
			checkDepth(depth);
		}
		
		final String name = (type != NBTConstants.TYPE_END) ? readString("tag name") : "";
		
		return readTagPayload(type, name, depth);
	}
	
	/**
	 * Reads the payload of a tag, given the name and type.
	 * 
	 * @param type
	 *            The type.
	 * @param name
	 *            The name.
	 * @param depth
	 *            The depth.
	 * @return The tag.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	private Tag readTagPayload(final int type, final String name, final int depth)
			throws IOException
	{
	
		switch (type)
			{
				case NBTConstants.TYPE_END :
					if (depth == 0) {
						throw new IOException(
								"[JNBT] TAG_End found without a TAG_Compound/TAG_List tag preceding it.");
					} else {
						return new EndTag();
					}
				case NBTConstants.TYPE_BYTE :
					return new ByteTag(name, is.readByte());
				case NBTConstants.TYPE_SHORT :
					return new ShortTag(name, is.readShort());
				case NBTConstants.TYPE_INT :
					return new IntTag(name, is.readInt());
				case NBTConstants.TYPE_LONG :
					return new LongTag(name, is.readLong());
				case NBTConstants.TYPE_FLOAT :
					return new FloatTag(name, is.readFloat());
				case NBTConstants.TYPE_DOUBLE :
					return new DoubleTag(name, is.readDouble());
				case NBTConstants.TYPE_BYTE_ARRAY :
					int length = readLength("byte array", limits.getMaxArrayLength());
					byte[] bytes = new byte[length];
					is.readFully(bytes);
					return new ByteArrayTag(name, bytes);
				case NBTConstants.TYPE_STRING :
					return new StringTag(name, readString("string"));
				case NBTConstants.TYPE_LIST :
					final int childType = is.readByte() & 0xFF;
					length = readLength("list", limits.getMaxListLength());
					final Class<? extends Tag> listType;
					try {
						listType = NBTUtils.getTypeClass(childType);
					} catch (IllegalArgumentException e) {
						throw new IOException("[JNBT] Invalid list element type: " + childType + ".", e);
					}
					
					final List<Tag> tagList = new ArrayList<Tag>();
					for (int i = 0; i < length; i++) {
						checkDepth(depth + 1);
						final Tag tag = readTagPayload(childType, "", depth + 1);
						if (tag instanceof EndTag) { throw new IOException(
								"[JNBT] TAG_End not permitted in a list."); }
						tagList.add(tag);
					}
					
					return new ListTag(name, listType, tagList);
				case NBTConstants.TYPE_COMPOUND :
					// Keep the stream's order so a read and write round trip is byte-for-byte stable.
					final Map<String, Tag> tagMap = new LinkedHashMap<String, Tag>();
					while (true) {
						final Tag tag = readTag(depth + 1);
						if (tag instanceof EndTag) {
							break;
						} else {
							tagMap.put(tag.getName(), tag);
						}
					}
					
					return new CompoundTag(name, tagMap);
				case NBTConstants.TYPE_INT_ARRAY :
					length = readLength("int array", limits.getMaxArrayLength());
					final int[] ints = new int[length];
					for (int i = 0; i < length; i++) {
						ints[i] = is.readInt();
					}
					return new IntArrayTag(name, ints);
				case NBTConstants.TYPE_LONG_ARRAY :
					length = readLength("long array", limits.getMaxArrayLength());
					final long[] longs = new long[length];
					for (int i = 0; i < length; i++) {
						longs[i] = is.readLong();
					}
					return new LongArrayTag(name, longs);
				default :
					throw new IOException("[JNBT] Invalid tag type: " + type
							+ ".");
			}
	}

	/**
	 * Reads a length-prefixed string in Java's modified UTF-8, the encoding NBT
	 * uses for tag names and string values. The length is checked against the
	 * string limit before the bytes are allocated.
	 */
	private String readString(final String kind) throws IOException {
		final int length = is.readUnsignedShort();
		checkLength(kind, length, limits.getMaxStringBytes());
		final byte[] encoded = new byte[length + 2];
		encoded[0] = (byte) (length >>> 8);
		encoded[1] = (byte) length;
		is.readFully(encoded, 2, length);
		// readUTF decodes modified UTF-8 and rejects malformed input with an IOException.
		return DataInputStream.readUTF(new DataInputStream(new ByteArrayInputStream(encoded)));
	}

	private int readLength(final String kind, final int maximum) throws IOException {
		final int length = is.readInt();
		checkLength(kind, length, maximum);
		return length;
	}

	private void checkLength(final String kind, final int length, final int maximum)
			throws IOException {
		if (length < 0 || length > maximum) {
			throw new IOException("[JNBT] Invalid " + kind + " length: " + length
					+ " (maximum " + maximum + ").");
		}
	}

	private void checkDepth(final int depth) throws IOException {
		if (depth > limits.getMaxDepth()) {
			throw new IOException("[JNBT] Maximum tag depth exceeded: " + depth + ".");
		}
	}
	
	@Override
	public void close() throws IOException {
	
		is.close();
	}
}
