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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
	private final NBTCompression compression;
	private final NBTFormat format;

	/**
	 * A stream whose compression has been detected, with the bytes read for
	 * detection pushed back so that nothing is lost.
	 */
	private record Detected(InputStream stream, NBTCompression compression) {
		static Detected of(final InputStream input) throws IOException {
			if (input == null) {
				throw new NullPointerException("Input is required.");
			}
			try {
				final PushbackInputStream pushback = new PushbackInputStream(input,
						NBTCompression.DETECT_BYTES);
				final byte[] start = pushback.readNBytes(NBTCompression.DETECT_BYTES);
				pushback.unread(start);
				if (start.length == 0) {
					throw new EOFException("[JNBT] The NBT stream is empty.");
				}
				final NBTCompression compression = NBTCompression.detect(start).orElseThrow(
						() -> new IOException(String.format(
								"[JNBT] Unrecognized NBT compression: the stream starts with 0x%02X.",
								start[0] & 0xFF)));
				return new Detected(pushback, compression);
			} catch (IOException | RuntimeException e) {
				try {
					input.close();
				} catch (IOException suppressed) {
					e.addSuppressed(suppressed);
				}
				throw e;
			}
		}
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that detects the compression
	 * of the specified input stream from its first bytes, with the
	 * {@linkplain NBTReadLimits#DEFAULT default read limits}. See
	 * {@link NBTCompression#detect(byte[])}.
	 *
	 * @param input
	 *            The input stream.
	 * @throws IOException
	 *             if an I/O error occurs, the stream is empty, or its
	 *             compression is not recognized. The input stream is closed
	 *             before this is thrown.
	 */
	public NBTInputStream(final InputStream input) throws IOException {
		this(input, NBTReadLimits.DEFAULT);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that detects the compression
	 * of the specified input stream from its first bytes, with the given read
	 * limits. See {@link NBTCompression#detect(byte[])}.
	 *
	 * @param input
	 *            The input stream.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 * @throws IOException
	 *             if an I/O error occurs, the stream is empty, or its
	 *             compression is not recognized. The input stream is closed
	 *             before this is thrown.
	 */
	public NBTInputStream(final InputStream input, final NBTReadLimits limits) throws IOException {
		this(input, limits, NBTFormat.JAVA);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> for the given format that
	 * detects the compression of the specified input stream from its first
	 * bytes, with the {@linkplain NBTReadLimits#DEFAULT default read limits}.
	 * See {@link NBTCompression#detect(byte[])}.
	 *
	 * @param input
	 *            The input stream.
	 * @param format
	 *            The binary format, such as {@link NBTFormat#BEDROCK}.
	 * @throws IOException
	 *             if an I/O error occurs, the stream is empty, or its
	 *             compression is not recognized. The input stream is closed
	 *             before this is thrown.
	 */
	public NBTInputStream(final InputStream input, final NBTFormat format) throws IOException {
		this(input, NBTReadLimits.DEFAULT, format);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> for the given format that
	 * detects the compression of the specified input stream from its first
	 * bytes, with the given read limits. See
	 * {@link NBTCompression#detect(byte[])}.
	 *
	 * @param input
	 *            The input stream.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 * @param format
	 *            The binary format, such as {@link NBTFormat#BEDROCK}.
	 * @throws IOException
	 *             if an I/O error occurs, the stream is empty, or its
	 *             compression is not recognized. The input stream is closed
	 *             before this is thrown.
	 */
	public NBTInputStream(final InputStream input, final NBTReadLimits limits,
			final NBTFormat format) throws IOException {
		this(Detected.of(input), limits, format);
	}

	private NBTInputStream(final Detected detected, final NBTReadLimits limits,
			final NBTFormat format) throws IOException {
		this(detected.stream(), detected.compression(), limits, format);
	}

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
		this.compression = NBTCompression.NONE;
		this.format = NBTFormat.JAVA;
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
		this(input, compression, limits, NBTFormat.JAVA);
	}

	/**
	 * Creates a new <code>NBTInputStream</code> that reads the given format
	 * from the specified input stream with the given compression and read
	 * limits.
	 *
	 * @param input
	 *            The input stream.
	 * @param compression
	 *            The compression applied to the stream.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 * @param format
	 *            The binary format, such as {@link NBTFormat#BEDROCK}.
	 * @throws IOException
	 *             if an I/O error occurs, such as a missing or corrupt GZip
	 *             header. The input stream is closed before this is thrown.
	 */
	public NBTInputStream(InputStream input, NBTCompression compression,
			NBTReadLimits limits, NBTFormat format) throws IOException {
		if (input == null || compression == null || limits == null || format == null) {
			throw new NullPointerException("Input, compression, limits and format are required.");
		}
		try {
			if (compression == NBTCompression.GZIP) {
				input = new GZIPInputStream(input);
			} else if (compression == NBTCompression.DEFLATE) {
				input = new InflaterInputStream(input);
			} else if (compression == NBTCompression.LZ4) {
				input = new LZ4BlockInputStream(input);
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
		this.compression = compression;
		this.format = format;
	}

	/**
	 * Gets the binary format of this stream.
	 *
	 * @return The format.
	 */
	public NBTFormat getFormat() {
		return format;
	}

	/**
	 * Gets the compression of this stream: the one given when it was created,
	 * or the one detected. Useful for writing a file back the same way.
	 *
	 * @return The compression.
	 */
	public NBTCompression getCompression() {
		return compression;
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
					return new ShortTag(name, readShort());
				case NBTConstants.TYPE_INT :
					return new IntTag(name, readInt());
				case NBTConstants.TYPE_LONG :
					return new LongTag(name, readLong());
				case NBTConstants.TYPE_FLOAT :
					return new FloatTag(name, readFloat());
				case NBTConstants.TYPE_DOUBLE :
					return new DoubleTag(name, readDouble());
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
						ints[i] = readInt();
					}
					return new IntArrayTag(name, ints);
				case NBTConstants.TYPE_LONG_ARRAY :
					length = readLength("long array", limits.getMaxArrayLength());
					final long[] longs = new long[length];
					for (int i = 0; i < length; i++) {
						longs[i] = readLong();
					}
					return new LongArrayTag(name, longs);
				default :
					throw new IOException("[JNBT] Invalid tag type: " + type
							+ ".");
			}
	}

	/**
	 * Reads a length-prefixed tag name or string value: modified UTF-8 in the
	 * Java format, standard UTF-8 in the Bedrock format. The length is checked
	 * against the string limit before the bytes are allocated.
	 */
	private String readString(final String kind) throws IOException {
		final int length = readShort() & 0xFFFF;
		checkLength(kind, length, limits.getMaxStringBytes());
		if (format == NBTFormat.BEDROCK) {
			final byte[] encoded = new byte[length];
			is.readFully(encoded);
			// A strict decoder rejects malformed input with an IOException
			// (CharacterCodingException) instead of substituting characters.
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(encoded)).toString();
		}
		final byte[] encoded = new byte[length + 2];
		encoded[0] = (byte) (length >>> 8);
		encoded[1] = (byte) length;
		is.readFully(encoded, 2, length);
		// readUTF decodes modified UTF-8 and rejects malformed input with an IOException.
		return DataInputStream.readUTF(new DataInputStream(new ByteArrayInputStream(encoded)));
	}

	// Number readers: big-endian in the Java format, little-endian in the Bedrock format.

	private short readShort() throws IOException {
		final short value = is.readShort();
		return format == NBTFormat.BEDROCK ? Short.reverseBytes(value) : value;
	}

	private int readInt() throws IOException {
		final int value = is.readInt();
		return format == NBTFormat.BEDROCK ? Integer.reverseBytes(value) : value;
	}

	private long readLong() throws IOException {
		final long value = is.readLong();
		return format == NBTFormat.BEDROCK ? Long.reverseBytes(value) : value;
	}

	private float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt());
	}

	private double readDouble() throws IOException {
		return Double.longBitsToDouble(readLong());
	}

	private int readLength(final String kind, final int maximum) throws IOException {
		final int length = readInt();
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
