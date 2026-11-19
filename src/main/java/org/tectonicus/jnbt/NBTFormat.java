package org.tectonicus.jnbt;

/**
 * The binary layout of an NBT stream, which differs between Minecraft
 * editions. The tag types and structure are the same in every format; only
 * the encoding of numbers and strings differs.
 */
public enum NBTFormat {
	/**
	 * Java Edition: numbers are big-endian, and tag names and string values
	 * are in Java's modified UTF-8 (see {@link java.io.DataInput#readUTF()}).
	 */
	JAVA,
	/**
	 * Bedrock Edition files, such as <code>level.dat</code> and
	 * <code>.mcstructure</code>: numbers, including string lengths, are
	 * little-endian, and tag names and string values are in standard UTF-8.
	 * A Bedrock <code>level.dat</code> also has an 8-byte header before the
	 * NBT; use {@link BedrockLevelDat} to read and write it. Bedrock's network
	 * format, which stores numbers as variable-length integers, is different
	 * and not supported.
	 */
	BEDROCK
}
