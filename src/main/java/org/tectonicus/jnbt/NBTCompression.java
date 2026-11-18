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
	DEFLATE
}
