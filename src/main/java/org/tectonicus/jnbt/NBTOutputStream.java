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

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;


/**
 * <p>
 * This class writes <strong>NBT</strong>, or <strong>Named Binary Tag</strong>
 * <code>Tag</code> objects to an underlying <code>OutputStream</code>.
 * </p>
 * 
 * <p>
 * The NBT format was created by Markus Persson. It is described at
 * <a href="https://minecraft.wiki/w/NBT_format">https://minecraft.wiki/w/NBT_format</a>.
 * </p>
 * 
 * @author Graham Edgecombe, Jocopa3
 * 
 */
public final class NBTOutputStream implements Closeable {
	/**
	 * The output stream.
	 */
	private final DataOutputStream os;
	private final NBTFormat format;

	/**
	 * Creates a new <code>NBTOutputStream</code>, which will write data to the
	 * specified underlying output stream, GZip-compressed.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public NBTOutputStream(final OutputStream os) throws IOException {

		this(os, NBTCompression.GZIP);
	}

	
	/**
	 * Creates a new <code>NBTOutputStream</code>, which will write data to the
	 * specified underlying output stream.
	 * 
	 * @param os
	 *            The output stream.
	 * @param gzipped
	 *            Whether the output stream should be GZip-compressed.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public NBTOutputStream(final OutputStream os, final boolean gzipped) throws IOException {
		this(os, gzipped ? NBTCompression.GZIP : NBTCompression.NONE);
	}

	/**
	 * Creates a new <code>NBTOutputStream</code>, which will write data to the
	 * specified underlying output stream with the given compression.
	 *
	 * @param output
	 *            The output stream.
	 * @param compression
	 *            The compression to apply to the stream.
	 * @throws IOException
	 *             if an I/O error occurs, such as failing to write the GZip
	 *             header. The output stream is closed before this is thrown.
	 */
	public NBTOutputStream(OutputStream output, NBTCompression compression) throws IOException {
		this(output, compression, NBTFormat.JAVA);
	}

	/**
	 * Creates a new <code>NBTOutputStream</code>, which will write the given
	 * format to the specified underlying output stream with the given
	 * compression.
	 *
	 * @param output
	 *            The output stream.
	 * @param compression
	 *            The compression to apply to the stream.
	 * @param format
	 *            The binary format, such as {@link NBTFormat#BEDROCK}.
	 * @throws IOException
	 *             if an I/O error occurs, such as failing to write the GZip
	 *             header. The output stream is closed before this is thrown.
	 */
	public NBTOutputStream(OutputStream output, NBTCompression compression, NBTFormat format)
			throws IOException {
		if (output == null || compression == null || format == null) {
			throw new NullPointerException("Output, compression and format are required.");
		}
		this.format = format;
		try {
			if (compression == NBTCompression.GZIP) {
				output = new GZIPOutputStream(output);
			} else if (compression == NBTCompression.DEFLATE) {
				output = new DeflaterOutputStream(output);
			} else if (compression == NBTCompression.LZ4) {
				output = new LZ4BlockOutputStream(output);
			}
		} catch (IOException | RuntimeException e) {
			// The stream would otherwise leak: the caller never gets an object to close.
			try {
				output.close();
			} catch (IOException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw e;
		}
		this.os = new DataOutputStream(output);
	}
	
	/**
	 * Writes a tag.
	 * 
	 * @param tag
	 *            The tag to write.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void writeTag(final Tag tag) throws IOException {
	
		final int type = NBTUtils.getTypeCode(tag.getClass());
		final String name = tag.getName();
		checkString("Tag name", name);
		if (type == NBTConstants.TYPE_END) {
			throw new IOException("[JNBT] Named TAG_End not permitted.");
		}

		os.writeByte(type);
		writeString(name);
		
		writeTagPayload(tag);
	}
	
	/**
	 * Writes the payload of a tag: everything after its type and name.
	 * 
	 * @param tag
	 *            The tag.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	private void writeTagPayload(final Tag tag) throws IOException {
	
		switch (tag) {
			case EndTag t -> { /* TAG_End has no payload. */ }
			case ByteTag t -> os.writeByte(t.getValue());
			case ShortTag t -> writeShort(t.getValue());
			case IntTag t -> writeInt(t.getValue());
			case LongTag t -> writeLong(t.getValue());
			case FloatTag t -> writeInt(Float.floatToIntBits(t.getValue()));
			case DoubleTag t -> writeLong(Double.doubleToLongBits(t.getValue()));
			case ByteArrayTag t -> {
				final byte[] bytes = t.getValue();
				writeInt(bytes.length);
				os.write(bytes);
			}
			case StringTag t -> {
				checkString("String value", t.getValue());
				writeString(t.getValue());
			}
			case ListTag t -> {
				final List<Tag> tags = t.getValue();
				os.writeByte(NBTUtils.getTypeCode(t.getType()));
				writeInt(tags.size());
				for (final Tag element : tags) {
					writeTagPayload(element);
				}
			}
			case CompoundTag t -> {
				for (final Tag child : t.getValue().values()) {
					writeTag(child);
				}
				os.writeByte(NBTConstants.TYPE_END);
			}
			case IntArrayTag t -> {
				writeInt(t.length());
				for (int i = 0; i < t.length(); i++) {
					writeInt(t.get(i));
				}
			}
			case LongArrayTag t -> {
				writeInt(t.length());
				for (int i = 0; i < t.length(); i++) {
					writeLong(t.get(i));
				}
			}
			default -> throw new IOException("[JNBT] Invalid tag class: "
					+ tag.getClass().getName() + ".");
		}
	}

	/**
	 * Checks that a string fits in NBT's 65535-byte limit once encoded, before
	 * anything is written, so an oversized string does not leave a partial tag
	 * in the stream.
	 *
	 * @param kind
	 *            What the string is, for the error message.
	 * @param value
	 *            The string.
	 * @throws IOException
	 *             if the encoded string is longer than 65535 bytes.
	 */
	private void checkString(final String kind, final String value) throws IOException {
		long length = 0;
		if (format == NBTFormat.BEDROCK) {
			length = encodeUtf8(value).length;
		} else {
			for (int i = 0; i < value.length(); i++) {
				final char c = value.charAt(i);
				// Modified UTF-8 encodes U+0000 in two bytes and each UTF-16 surrogate in three.
				length += (c >= 0x0001 && c <= 0x007F) ? 1 : (c <= 0x07FF) ? 2 : 3;
			}
		}
		if (length > 65535) {
			throw new IOException("[JNBT] " + kind + " exceeds the 65535 byte NBT limit.");
		}
	}

	/**
	 * Writes a length-prefixed tag name or string value, already checked by
	 * {@link #checkString(String, String)}.
	 */
	private void writeString(final String value) throws IOException {
		if (format == NBTFormat.BEDROCK) {
			final byte[] bytes = encodeUtf8(value);
			writeShort((short) bytes.length);
			os.write(bytes);
		} else {
			os.writeUTF(value);
		}
	}

	/**
	 * Encodes a string in standard UTF-8, rejecting unpaired surrogates rather
	 * than silently replacing them.
	 */
	private static byte[] encodeUtf8(final String value) throws IOException {
		final ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.encode(CharBuffer.wrap(value));
		final byte[] bytes = new byte[encoded.remaining()];
		encoded.get(bytes);
		return bytes;
	}

	// Number writers: big-endian in the Java format, little-endian in the Bedrock format.

	private void writeShort(final short value) throws IOException {
		os.writeShort(format == NBTFormat.BEDROCK ? Short.reverseBytes(value) : value);
	}

	private void writeInt(final int value) throws IOException {
		os.writeInt(format == NBTFormat.BEDROCK ? Integer.reverseBytes(value) : value);
	}

	private void writeLong(final long value) throws IOException {
		os.writeLong(format == NBTFormat.BEDROCK ? Long.reverseBytes(value) : value);
	}

	/**
	 * Gets the binary format this stream writes.
	 *
	 * @return The format.
	 */
	public NBTFormat getFormat() {
		return format;
	}

	@Override
	public void close() throws IOException {
	
		os.close();
	}
}
