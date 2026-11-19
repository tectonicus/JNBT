package org.tectonicus.jnbt;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * The LZ4 block codec and the framing that Minecraft uses for LZ4-compressed
 * region file chunks: the format written by lz4-java's
 * <code>LZ4BlockOutputStream</code> with its default settings.
 * <p>
 * The stream is a series of blocks, each made of a 21-byte header and a
 * payload. The header holds the magic <code>LZ4Block</code>, a token byte (the
 * compression method in the high nibble and the block size, as a power of two
 * above 1024, in the low nibble), then the little-endian compressed length,
 * decompressed length and checksum. The checksum is XXHash32 of the
 * decompressed bytes, seeded with <code>0x9747b28c</code> and masked to its low
 * 28 bits. The payload is stored raw when compressing would not shrink it. A
 * block with both lengths zero ends the stream.
 * </p>
 */
final class LZ4Block {
	static final byte[] MAGIC = { 'L', 'Z', '4', 'B', 'l', 'o', 'c', 'k' };
	static final int HEADER_LENGTH = MAGIC.length + 1 + 4 + 4 + 4;

	static final int METHOD_RAW = 0x10;
	static final int METHOD_LZ4 = 0x20;
	static final int COMPRESSION_LEVEL_BASE = 10;
	/** The block size lz4-java, and therefore Minecraft, uses by default. */
	static final int DEFAULT_BLOCK_SIZE = 1 << 16;
	/** Token low nibble for {@link #DEFAULT_BLOCK_SIZE}. */
	static final int DEFAULT_COMPRESSION_LEVEL = 16 - COMPRESSION_LEVEL_BASE;
	static final int CHECKSUM_SEED = 0x9747b28c;
	static final int CHECKSUM_MASK = 0x0FFFFFFF;

	private static final int MIN_MATCH = 4;
	private static final int LAST_LITERALS = 5;
	private static final int MF_LIMIT = 12;
	private static final int MAX_DISTANCE = 65535;
	static final int HASH_LOG = 12;
	private static final int SKIP_STRENGTH = 6;

	// Little-endian int and long views of byte arrays; the JIT compiles each access to one bounds-checked load.
	private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class,
			ByteOrder.LITTLE_ENDIAN);
	private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(long[].class,
			ByteOrder.LITTLE_ENDIAN);

	private LZ4Block() {
	}

	/**
	 * Gets the most bytes an LZ4 block of the given length can compress to.
	 */
	static int maxCompressedLength(final int length) {
		return length + length / 255 + 16;
	}

	/**
	 * Compresses <code>src[0, length)</code> as one LZ4 block into
	 * <code>dest</code> from <code>destOffset</code>, which must leave room for
	 * {@link #maxCompressedLength(int)} bytes.
	 *
	 * @param table
	 *            Scratch space of <code>1 &lt;&lt; HASH_LOG</code> entries.
	 * @return The compressed length.
	 */
	static int compress(final byte[] src, final int length, final byte[] dest, final int destOffset,
			final int[] table) {
		int dp = destOffset;
		int anchor = 0;
		if (length > MF_LIMIT) {
			Arrays.fill(table, -1);
			// A match may start no later than mfLimit and must end at least LAST_LITERALS before the end.
			final int mfLimit = length - MF_LIMIT;
			final int matchLimit = length - LAST_LITERALS;
			int ip = 0;
			int misses = 1 << SKIP_STRENGTH;
			while (ip <= mfLimit) {
				final int sequence = readIntLE(src, ip);
				final int hash = hash(sequence);
				int ref = table[hash];
				table[hash] = ip;
				if (ref < 0 || ip - ref > MAX_DISTANCE || readIntLE(src, ref) != sequence) {
					// Step further the longer nothing matches, so incompressible data is fast.
					ip += misses++ >>> SKIP_STRENGTH;
					continue;
				}
				misses = 1 << SKIP_STRENGTH;
				while (ip > anchor && ref > 0 && src[ip - 1] == src[ref - 1]) {
					ip--;
					ref--;
				}
				final int matchLength = MIN_MATCH
						+ commonLength(src, ip + MIN_MATCH, ref + MIN_MATCH, matchLimit);
				dp = writeSequence(src, anchor, ip - anchor, ip - ref, matchLength, dest, dp);
				ip += matchLength;
				anchor = ip;
				if (ip - 2 >= 0 && ip - 2 <= mfLimit) {
					table[hash(readIntLE(src, ip - 2))] = ip - 2;
				}
			}
		}
		return writeSequence(src, anchor, length - anchor, 0, 0, dest, dp) - destOffset;
	}

	/**
	 * Counts how many bytes from <code>ip</code> equal those from
	 * <code>ref</code>, stopping at <code>limit</code>. Compares eight bytes at
	 * a time and finds the first differing byte from the XOR.
	 */
	private static int commonLength(final byte[] src, final int ip, final int ref, final int limit) {
		int length = 0;
		while (ip + length <= limit - 8) {
			final long diff = (long) LONG_LE.get(src, ip + length) ^ (long) LONG_LE.get(src, ref + length);
			if (diff != 0) {
				return length + (Long.numberOfTrailingZeros(diff) >>> 3);
			}
			length += 8;
		}
		while (ip + length < limit && src[ip + length] == src[ref + length]) {
			length++;
		}
		return length;
	}

	/**
	 * Writes one sequence: literals and then, unless <code>matchLength</code>
	 * is zero (the final sequence), a match.
	 */
	private static int writeSequence(final byte[] src, final int literalStart, final int literalLength,
			final int offset, final int matchLength, final byte[] dest, int dp) {
		final int matchCode = matchLength == 0 ? 0 : matchLength - MIN_MATCH;
		final int tokenPosition = dp++;
		dest[tokenPosition] = (byte) ((Math.min(literalLength, 15) << 4) | Math.min(matchCode, 15));
		dp = writeLength(literalLength, dest, dp);
		System.arraycopy(src, literalStart, dest, dp, literalLength);
		dp += literalLength;
		if (matchLength != 0) {
			dest[dp++] = (byte) offset;
			dest[dp++] = (byte) (offset >>> 8);
			dp = writeLength(matchCode, dest, dp);
		}
		return dp;
	}

	private static int writeLength(int length, final byte[] dest, int dp) {
		if (length >= 15) {
			length -= 15;
			while (length >= 255) {
				dest[dp++] = (byte) 255;
				length -= 255;
			}
			dest[dp++] = (byte) length;
		}
		return dp;
	}

	/**
	 * Decompresses an LZ4 block that must produce exactly
	 * <code>destLength</code> bytes and use all of its input.
	 *
	 * @throws IOException
	 *             if the block is malformed.
	 */
	static void decompress(final byte[] src, final int srcLength, final byte[] dest, final int destLength)
			throws IOException {
		int sp = 0;
		int dp = 0;
		while (true) {
			if (sp >= srcLength) {
				throw corrupt("block ended unexpectedly");
			}
			final int token = src[sp++] & 0xFF;

			int literalLength = token >>> 4;
			if (literalLength == 15) {
				int b;
				do {
					if (sp >= srcLength) {
						throw corrupt("block ended unexpectedly");
					}
					b = src[sp++] & 0xFF;
					literalLength += b;
				} while (b == 255 && literalLength <= destLength);
			}
			if (literalLength > srcLength - sp || literalLength > destLength - dp) {
				throw corrupt("literals overrun the block");
			}
			if (literalLength <= SHORT_COPY && sp + SHORT_COPY + 8 <= src.length
					&& dp + SHORT_COPY + 8 <= dest.length) {
				shortCopy(src, sp, dest, dp, literalLength);
			} else {
				System.arraycopy(src, sp, dest, dp, literalLength);
			}
			sp += literalLength;
			dp += literalLength;
			if (sp == srcLength) {
				break;
			}

			if (srcLength - sp < 2) {
				throw corrupt("block ended unexpectedly");
			}
			final int offset = (src[sp] & 0xFF) | (src[sp + 1] & 0xFF) << 8;
			sp += 2;
			if (offset == 0 || offset > dp) {
				throw corrupt("invalid match offset " + offset);
			}
			int matchLength = token & 0x0F;
			if (matchLength == 15) {
				int b;
				do {
					if (sp >= srcLength) {
						throw corrupt("block ended unexpectedly");
					}
					b = src[sp++] & 0xFF;
					matchLength += b;
				} while (b == 255 && matchLength <= destLength);
			}
			matchLength += MIN_MATCH;
			if (matchLength > destLength - dp) {
				throw corrupt("match overruns the block");
			}
			final int ref = dp - offset;
			final int end = dp + matchLength;
			if (offset >= 8 && matchLength <= SHORT_COPY && dp + SHORT_COPY + 8 <= dest.length) {
				// Each 8-byte step reads only bytes written before it, since they are at least 8 back.
				shortCopy(dest, ref, dest, dp, matchLength);
				dp = end;
				continue;
			}
			// A match closer than its length overlaps its own output, repeating every offset bytes. Copying
			// everything from ref written so far doubles the run each pass and keeps each copy non-overlapping.
			while (dp < end) {
				final int count = Math.min(end - dp, dp - ref);
				System.arraycopy(dest, ref, dest, dp, count);
				dp += count;
			}
		}
		if (dp != destLength) {
			throw corrupt("decompressed " + dp + " bytes, expected " + destLength);
		}
	}

	/** The longest copy {@link #shortCopy} handles; longer ones go to System.arraycopy. */
	private static final int SHORT_COPY = 32;

	/**
	 * Copies <code>length</code> bytes, at most {@link #SHORT_COPY}, eight at a
	 * time. It may write up to 7 bytes past the end of the copy, which callers
	 * allow for: those bytes lie inside the array and are overwritten later or
	 * past the end of the block. Short copies are most of the work in LZ4
	 * decompression, and System.arraycopy has a fixed cost per call that
	 * dominates at these sizes.
	 */
	private static void shortCopy(final byte[] src, final int sp, final byte[] dest, final int dp,
			final int length) {
		for (int i = 0; i < length; i += 8) {
			LONG_LE.set(dest, dp + i, (long) LONG_LE.get(src, sp + i));
		}
	}

	private static IOException corrupt(final String reason) {
		return new IOException("[JNBT] Corrupt LZ4 block: " + reason + ".");
	}

	private static int hash(final int sequence) {
		return (sequence * -1640531535) >>> (32 - HASH_LOG);
	}

	static int readIntLE(final byte[] b, final int i) {
		return (int) INT_LE.get(b, i);
	}

	static void writeIntLE(final byte[] b, final int i, final int value) {
		INT_LE.set(b, i, value);
	}

	/** Gets the block checksum: masked XXHash32 of <code>b[0, length)</code>. */
	static int checksum(final byte[] b, final int length) {
		return xxHash32(b, 0, length, CHECKSUM_SEED) & CHECKSUM_MASK;
	}

	private static final int PRIME1 = 0x9E3779B1;
	private static final int PRIME2 = 0x85EBCA77;
	private static final int PRIME3 = 0xC2B2AE3D;
	private static final int PRIME4 = 0x27D4EB2F;
	private static final int PRIME5 = 0x165667B1;

	static int xxHash32(final byte[] b, int off, final int length, final int seed) {
		final int end = off + length;
		int h;
		if (length >= 16) {
			int v1 = seed + PRIME1 + PRIME2;
			int v2 = seed + PRIME2;
			int v3 = seed;
			int v4 = seed - PRIME1;
			final int limit = end - 16;
			do {
				v1 = round(v1, readIntLE(b, off));
				v2 = round(v2, readIntLE(b, off + 4));
				v3 = round(v3, readIntLE(b, off + 8));
				v4 = round(v4, readIntLE(b, off + 12));
				off += 16;
			} while (off <= limit);
			h = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
					+ Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
		} else {
			h = seed + PRIME5;
		}
		h += length;
		while (off <= end - 4) {
			h += readIntLE(b, off) * PRIME3;
			h = Integer.rotateLeft(h, 17) * PRIME4;
			off += 4;
		}
		while (off < end) {
			h += (b[off] & 0xFF) * PRIME5;
			h = Integer.rotateLeft(h, 11) * PRIME1;
			off++;
		}
		h ^= h >>> 15;
		h *= PRIME2;
		h ^= h >>> 13;
		h *= PRIME3;
		h ^= h >>> 16;
		return h;
	}

	private static int round(int acc, final int input) {
		acc += input * PRIME2;
		acc = Integer.rotateLeft(acc, 13);
		return acc * PRIME1;
	}
}
