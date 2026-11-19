package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Checks JNBT's LZ4 support against lz4-java, the library Minecraft uses to
 * read and write LZ4-compressed region file chunks.
 */
class LZ4CompressionTest {

	/** Inputs of many shapes: empty, tiny, compressible, random, and spanning several blocks. */
	private static List<byte[]> samples() {
		final Random random = new Random(42);
		final List<byte[]> samples = new ArrayList<byte[]>();
		samples.add(new byte[0]);
		samples.add(new byte[] { 7 });
		samples.add("hello hello hello hello hello".getBytes());
		samples.add(new byte[100_000]);

		final byte[] noise = new byte[200_000];
		random.nextBytes(noise);
		samples.add(noise);

		// Runs of repeated short patterns mixed with noise, like real chunk data.
		final byte[] mixed = new byte[300_000];
		for (int i = 0; i < mixed.length; i++) {
			mixed[i] = (i / 1000) % 3 == 0 ? (byte) random.nextInt() : (byte) (i % 7);
		}
		samples.add(mixed);
		return samples;
	}

	@Test
	void lz4JavaReadsWhatJnbtWrites() throws Exception {
		for (final byte[] sample : samples()) {
			final byte[] encoded = jnbtCompress(sample);
			try (InputStream in = new net.jpountz.lz4.LZ4BlockInputStream(new ByteArrayInputStream(encoded))) {
				assertArrayEquals(sample, in.readAllBytes());
			}
		}
	}

	@Test
	void jnbtReadsWhatLz4JavaWrites() throws Exception {
		for (final byte[] sample : samples()) {
			final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
			try (OutputStream out = new net.jpountz.lz4.LZ4BlockOutputStream(encoded)) {
				out.write(sample);
			}
			assertArrayEquals(sample, jnbtDecompress(encoded.toByteArray()));
		}
	}

	@Test
	void writesTheSameBytesAsLz4JavaForIncompressibleAndEmptyStreams() throws Exception {
		// Raw blocks leave the compressor no choices, so the framing must match exactly.
		final byte[] noise = new byte[150_000];
		new Random(1).nextBytes(noise);
		for (final byte[] sample : new byte[][] { new byte[0], noise }) {
			final ByteArrayOutputStream expected = new ByteArrayOutputStream();
			try (OutputStream out = new net.jpountz.lz4.LZ4BlockOutputStream(expected)) {
				out.write(sample);
			}
			assertArrayEquals(expected.toByteArray(), jnbtCompress(sample));
		}
	}

	@Test
	void compressesRepetitiveData() throws Exception {
		assertTrue(jnbtCompress(new byte[100_000]).length < 2_000);
	}

	@Test
	void compressedBlocksDecodeWithLz4JavasDecompressor() throws Exception {
		final Random random = new Random(7);
		final int[] table = new int[1 << LZ4Block.HASH_LOG];
		for (int run = 0; run < 2_000; run++) {
			final int length = random.nextInt(run < 200 ? 40 : 5_000);
			final int alphabet = 1 + random.nextInt(run % 2 == 0 ? 4 : 256);
			final byte[] source = new byte[length];
			for (int i = 0; i < length; i++) {
				source[i] = (byte) random.nextInt(alphabet);
			}
			final byte[] compressed = new byte[LZ4Block.maxCompressedLength(length) + 3];
			final int compressedLength = LZ4Block.compress(source, length, compressed, 3, table);

			final byte[] ours = new byte[length];
			final byte[] block = java.util.Arrays.copyOfRange(compressed, 3, 3 + compressedLength);
			LZ4Block.decompress(block, compressedLength, ours, length);
			assertArrayEquals(source, ours);

			final byte[] theirs = new byte[length];
			final int decoded = LZ4Factory.safeInstance().safeDecompressor()
					.decompress(compressed, 3, compressedLength, theirs, 0, length);
			assertEquals(length, decoded);
			assertArrayEquals(source, theirs);
		}
	}

	@Test
	void xxHash32MatchesLz4Java() {
		final Random random = new Random(3);
		for (int length = 0; length < 100; length++) {
			final byte[] data = new byte[length];
			random.nextBytes(data);
			final int seed = random.nextInt();
			assertEquals(XXHashFactory.safeInstance().hash32().hash(data, 0, length, seed),
					LZ4Block.xxHash32(data, 0, length, seed));
		}
	}

	@Test
	void readsAndWritesNbtThroughLz4() throws Exception {
		final Map<String, Tag> entries = new LinkedHashMap<String, Tag>();
		entries.put("name", new StringTag("name", "Chunk"));
		entries.put("blocks", new LongArrayTag("blocks", new long[50_000]));
		final CompoundTag root = new CompoundTag("", entries);

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(bytes, NBTCompression.LZ4)) {
			out.writeTag(root);
		}
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()),
				NBTCompression.LZ4)) {
			assertEquals(root, in.readTag());
		}

		// The same tag written the way Minecraft does reads back identically.
		final ByteArrayOutputStream minecraft = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(
				new net.jpountz.lz4.LZ4BlockOutputStream(minecraft), NBTCompression.NONE)) {
			out.writeTag(root);
		}
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(minecraft.toByteArray()),
				NBTCompression.LZ4)) {
			assertEquals(root, in.readTag());
		}
	}

	@Test
	void rejectsCorruptStreams() throws Exception {
		final byte[] sample = samples().get(5);
		final byte[] encoded = jnbtCompress(sample);

		final byte[] flipped = encoded.clone();
		flipped[LZ4Block.HEADER_LENGTH + 100] ^= 0x55;
		assertThrows(IOException.class, () -> jnbtDecompress(flipped));

		final byte[] badChecksum = encoded.clone();
		badChecksum[LZ4Block.HEADER_LENGTH - 1] ^= 0x01;
		assertThrows(IOException.class, () -> jnbtDecompress(badChecksum));

		final byte[] badMagic = encoded.clone();
		badMagic[0] = 'X';
		assertThrows(IOException.class, () -> jnbtDecompress(badMagic));

		final byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 30);
		assertThrows(IOException.class, () -> jnbtDecompress(truncated));

		// A header claiming a block larger than its size class allows.
		final byte[] oversized = encoded.clone();
		LZ4Block.writeIntLE(oversized, LZ4Block.MAGIC.length + 5, (1 << 16) + 1);
		assertThrows(IOException.class, () -> jnbtDecompress(oversized));
	}

	private static byte[] jnbtCompress(final byte[] data) throws IOException {
		final ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		try (OutputStream out = new LZ4BlockOutputStream(encoded)) {
			// Write in uneven pieces to cross block boundaries mid-write.
			for (int off = 0; off < data.length; off += 9_999) {
				out.write(data, off, Math.min(9_999, data.length - off));
			}
		}
		return encoded.toByteArray();
	}

	private static byte[] jnbtDecompress(final byte[] encoded) throws IOException {
		try (InputStream in = new LZ4BlockInputStream(new ByteArrayInputStream(encoded))) {
			return in.readAllBytes();
		}
	}
}
