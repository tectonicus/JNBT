package org.tectonicus.jnbt;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Reads the LZ4 block format described in {@link LZ4Block}, checking each
 * block's checksum. Reading stops at the end block, as lz4-java does by
 * default, so anything after it is left unread.
 */
final class LZ4BlockInputStream extends FilterInputStream {
	private static final byte[] EMPTY = new byte[0];

	private final byte[] header = new byte[LZ4Block.HEADER_LENGTH];
	private byte[] buffer = EMPTY;
	private byte[] compressed = EMPTY;
	private int position;
	private int limit;
	private boolean finished;

	LZ4BlockInputStream(final InputStream in) {
		super(in);
	}

	@Override
	public int read() throws IOException {
		if (!fill()) {
			return -1;
		}
		return buffer[position++] & 0xFF;
	}

	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		if (len == 0) {
			return 0;
		}
		if (!fill()) {
			return -1;
		}
		final int count = Math.min(len, limit - position);
		System.arraycopy(buffer, position, b, off, count);
		position += count;
		return count;
	}

	@Override
	public long skip(final long n) throws IOException {
		if (n <= 0 || !fill()) {
			return 0;
		}
		final int count = (int) Math.min(n, limit - position);
		position += count;
		return count;
	}

	@Override
	public int available() {
		return limit - position;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(final int readlimit) {
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	/**
	 * Makes sure decoded bytes are available, reading blocks as needed.
	 *
	 * @return false at the end of the stream.
	 */
	private boolean fill() throws IOException {
		while (position == limit) {
			if (finished) {
				return false;
			}
			readBlock();
		}
		return true;
	}

	private void readBlock() throws IOException {
		readFully(header, LZ4Block.HEADER_LENGTH);
		for (int i = 0; i < LZ4Block.MAGIC.length; i++) {
			if (header[i] != LZ4Block.MAGIC[i]) {
				throw new IOException("[JNBT] Not an LZ4 block stream: bad magic.");
			}
		}
		final int token = header[LZ4Block.MAGIC.length] & 0xFF;
		final int method = token & 0xF0;
		final int maxLength = 1 << (LZ4Block.COMPRESSION_LEVEL_BASE + (token & 0x0F));
		final int compressedLength = LZ4Block.readIntLE(header, LZ4Block.MAGIC.length + 1);
		final int originalLength = LZ4Block.readIntLE(header, LZ4Block.MAGIC.length + 5);
		final int check = LZ4Block.readIntLE(header, LZ4Block.MAGIC.length + 9);

		if (method != LZ4Block.METHOD_RAW && method != LZ4Block.METHOD_LZ4) {
			throw new IOException("[JNBT] Unknown LZ4 block compression method: " + method + ".");
		}
		if (originalLength < 0 || originalLength > maxLength || compressedLength < 0
				|| compressedLength > LZ4Block.maxCompressedLength(originalLength)
				|| (originalLength == 0) != (compressedLength == 0)
				|| (method == LZ4Block.METHOD_RAW && originalLength != compressedLength)) {
			throw new IOException("[JNBT] Corrupt LZ4 block header: compressed length "
					+ compressedLength + ", decompressed length " + originalLength + ".");
		}
		if (originalLength == 0) {
			if (check != 0) {
				throw new IOException("[JNBT] Corrupt LZ4 end block: non-zero checksum.");
			}
			finished = true;
			return;
		}

		if (buffer.length < originalLength) {
			buffer = new byte[originalLength];
		}
		if (method == LZ4Block.METHOD_RAW) {
			readFully(buffer, originalLength);
		} else {
			if (compressed.length < compressedLength) {
				compressed = new byte[compressedLength];
			}
			readFully(compressed, compressedLength);
			LZ4Block.decompress(compressed, compressedLength, buffer, originalLength);
		}
		if (LZ4Block.checksum(buffer, originalLength) != check) {
			throw new IOException("[JNBT] LZ4 block checksum mismatch.");
		}
		position = 0;
		limit = originalLength;
	}

	private void readFully(final byte[] b, final int length) throws IOException {
		int read = 0;
		while (read < length) {
			final int count = in.read(b, read, length - read);
			if (count < 0) {
				throw new EOFException("[JNBT] LZ4 stream ended unexpectedly.");
			}
			read += count;
		}
	}
}
