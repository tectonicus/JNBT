package org.tectonicus.jnbt;

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
	LZ4
}
