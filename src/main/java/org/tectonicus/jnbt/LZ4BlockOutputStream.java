package org.tectonicus.jnbt;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Writes the LZ4 block format described in {@link LZ4Block}, in 64 KiB blocks
 * as Minecraft does. Closing the stream writes the end block and closes the
 * underlying stream.
 */
final class LZ4BlockOutputStream extends FilterOutputStream {
	private static final int INITIAL_BUFFER = 4096;

	// Buffers grow as data arrives, since most chunks are much smaller than a block.
	private byte[] buffer = new byte[INITIAL_BUFFER];
	private byte[] compressed = new byte[LZ4Block.HEADER_LENGTH];
	private final int[] table = new int[1 << LZ4Block.HASH_LOG];
	private int position;
	private boolean closed;

	LZ4BlockOutputStream(final OutputStream out) {
		super(out);
	}

	@Override
	public void write(final int b) throws IOException {
		ensureOpen();
		makeRoom();
		buffer[position++] = (byte) b;
	}

	@Override
	public void write(final byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		ensureOpen();
		while (len > 0) {
			makeRoom();
			final int count = Math.min(len, buffer.length - position);
			System.arraycopy(b, off, buffer, position, count);
			position += count;
			off += count;
			len -= count;
		}
	}

	/**
	 * Flushes the underlying stream without ending the current block, as
	 * lz4-java does by default, so flushing does not hurt compression.
	 */
	@Override
	public void flush() throws IOException {
		ensureOpen();
		out.flush();
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		try {
			flushBlock();
			writeHeader(LZ4Block.METHOD_RAW, 0, 0, 0);
			out.write(compressed, 0, LZ4Block.HEADER_LENGTH);
			out.flush();
		} catch (IOException | RuntimeException e) {
			try {
				out.close();
			} catch (IOException suppressed) {
				e.addSuppressed(suppressed);
			}
			throw e;
		}
		out.close();
	}

	/** Makes room for at least one byte: grows the buffer up to a block, then writes full blocks out. */
	private void makeRoom() throws IOException {
		if (position == buffer.length) {
			if (buffer.length < LZ4Block.DEFAULT_BLOCK_SIZE) {
				buffer = Arrays.copyOf(buffer, Math.min(buffer.length * 2, LZ4Block.DEFAULT_BLOCK_SIZE));
			} else {
				flushBlock();
			}
		}
	}

	private void flushBlock() throws IOException {
		if (position == 0) {
			return;
		}
		final int needed = LZ4Block.HEADER_LENGTH + LZ4Block.maxCompressedLength(position);
		if (compressed.length < needed) {
			compressed = new byte[needed];
		}
		final int check = LZ4Block.checksum(buffer, position);
		int compressedLength = LZ4Block.compress(buffer, position, compressed, LZ4Block.HEADER_LENGTH, table);
		int method = LZ4Block.METHOD_LZ4;
		if (compressedLength >= position) {
			// Compression did not help, so store the block as-is, as lz4-java does.
			method = LZ4Block.METHOD_RAW;
			compressedLength = position;
			System.arraycopy(buffer, 0, compressed, LZ4Block.HEADER_LENGTH, position);
		}
		writeHeader(method, compressedLength, position, check);
		out.write(compressed, 0, LZ4Block.HEADER_LENGTH + compressedLength);
		position = 0;
	}

	private void writeHeader(final int method, final int compressedLength, final int originalLength,
			final int check) {
		System.arraycopy(LZ4Block.MAGIC, 0, compressed, 0, LZ4Block.MAGIC.length);
		compressed[LZ4Block.MAGIC.length] = (byte) (method | LZ4Block.DEFAULT_COMPRESSION_LEVEL);
		LZ4Block.writeIntLE(compressed, LZ4Block.MAGIC.length + 1, compressedLength);
		LZ4Block.writeIntLE(compressed, LZ4Block.MAGIC.length + 5, originalLength);
		LZ4Block.writeIntLE(compressed, LZ4Block.MAGIC.length + 9, check);
	}

	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("[JNBT] Stream closed.");
		}
	}
}
