package org.tectonicus.jnbt;

/**
 * Resource limits applied while reading an NBT stream.
 * <p>
 * Every length declared in the stream is checked against these limits before
 * any memory is allocated for it, so a corrupt or malicious file fails with an
 * {@link java.io.IOException} rather than exhausting the heap or the stack.
 * Instances are immutable.
 * </p>
 */
public final class NBTReadLimits {
	/**
	 * The limits used when none are given: a nesting depth of 512, strings of
	 * up to 65535 bytes, and arrays and lists of up to 16,777,216 elements.
	 */
	public static final NBTReadLimits DEFAULT = new NBTReadLimits(512, 65535,
			16 * 1024 * 1024, 16 * 1024 * 1024);

	private final int maxDepth;
	private final int maxStringBytes;
	private final int maxArrayLength;
	private final int maxListLength;

	/**
	 * Creates a set of read limits.
	 *
	 * @param maxDepth
	 *            The deepest that compound and list tags may nest.
	 * @param maxStringBytes
	 *            The largest string allowed, in encoded bytes. Cannot exceed
	 *            65535, the most the NBT format can represent.
	 * @param maxArrayLength
	 *            The most elements allowed in a byte, int or long array.
	 * @param maxListLength
	 *            The most elements allowed in a list tag.
	 * @throws IllegalArgumentException
	 *             if any limit is negative, or the string limit exceeds 65535.
	 */
	public NBTReadLimits(final int maxDepth, final int maxStringBytes,
			final int maxArrayLength, final int maxListLength) {
		if (maxDepth < 0 || maxStringBytes < 0 || maxStringBytes > 65535
				|| maxArrayLength < 0 || maxListLength < 0) {
			throw new IllegalArgumentException("NBT read limits must be non-negative; string limit cannot exceed 65535.");
		}
		this.maxDepth = maxDepth;
		this.maxStringBytes = maxStringBytes;
		this.maxArrayLength = maxArrayLength;
		this.maxListLength = maxListLength;
	}

	/**
	 * Gets the deepest that compound and list tags may nest.
	 *
	 * @return The maximum nesting depth.
	 */
	public int getMaxDepth() { return maxDepth; }

	/**
	 * Gets the largest string allowed, in encoded bytes.
	 *
	 * @return The maximum string length in bytes.
	 */
	public int getMaxStringBytes() { return maxStringBytes; }

	/**
	 * Gets the most elements allowed in a byte, int or long array.
	 *
	 * @return The maximum array length.
	 */
	public int getMaxArrayLength() { return maxArrayLength; }

	/**
	 * Gets the most elements allowed in a list tag.
	 *
	 * @return The maximum list length.
	 */
	public int getMaxListLength() { return maxListLength; }
}
