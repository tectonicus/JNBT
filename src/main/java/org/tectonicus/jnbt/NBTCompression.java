package org.tectonicus.jnbt;

import java.util.Arrays;
import java.util.Optional;

/**
 * The compression applied to an NBT stream, used when reading with
 * {@link NBTInputStream} and writing with {@link NBTOutputStream}.
 */
public enum NBTCompression {
	/** The stream is not compressed. */
	NONE,
	/**
	 * The stream is GZip-compressed, as Minecraft does for
	 * <code>level.dat</code> and player files.
	 */
	GZIP,
	/**
	 * The stream is zlib (Deflate) compressed, as Minecraft does for chunks
	 * inside region files.
	 */
	DEFLATE,
	/**
	 * The stream is LZ4-compressed in the block format of lz4-java's
	 * <code>LZ4BlockOutputStream</code>, as Minecraft 1.20.5 and later do for
	 * chunks inside region files when <code>region-file-compression</code> is
	 * set to <code>lz4</code> (compression type 4 in the chunk header). Each
	 * block's checksum is verified when reading.
	 */
	LZ4;

	/**
	 * The most leading bytes that {@link #detect(byte[])} looks at.
	 */
	public static final int DETECT_BYTES = 8;

	/**
	 * Works out the compression of an NBT stream from its first bytes.
	 * <p>
	 * GZip and LZ4 streams start with fixed magic bytes, and zlib streams with
	 * a two-byte header whose check bits make it a multiple of 31. An
	 * uncompressed stream starts with the root tag's type, from 0 to 12, which
	 * none of those headers can.
	 * </p>
	 *
	 * @param start
	 *            The first bytes of the stream: up to {@link #DETECT_BYTES},
	 *            or fewer if the stream is shorter.
	 * @return The compression, or an empty <code>Optional</code> if the bytes
	 *         match none of the formats.
	 */
	public static Optional<NBTCompression> detect(final byte[] start) {

		if (start.length == 0) {
			return Optional.empty();
		}
		final int first = start[0] & 0xFF;
		if (first <= NBTConstants.TYPE_LONG_ARRAY) {
			return Optional.of(NONE);
		}
		if (start.length >= 2) {
			final int second = start[1] & 0xFF;
			if (first == 0x1F && second == 0x8B) {
				return Optional.of(GZIP);
			}
			// zlib (RFC 1950): method 8 (Deflate), a window of at most 32 KiB,
			// and the two header bytes together a multiple of 31.
			if ((first & 0x0F) == 8 && (first >> 4) <= 7 && ((first << 8) | second) % 31 == 0) {
				return Optional.of(DEFLATE);
			}
		}
		if (start.length >= LZ4Block.MAGIC.length && Arrays.equals(start, 0,
				LZ4Block.MAGIC.length, LZ4Block.MAGIC, 0, LZ4Block.MAGIC.length)) {
			return Optional.of(LZ4);
		}
		return Optional.empty();
	}
}
