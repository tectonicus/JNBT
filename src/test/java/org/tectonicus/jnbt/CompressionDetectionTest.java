package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;

class CompressionDetectionTest {
	private static final CompoundTag ROOT;
	static {
		final Map<String, Tag> entries = new LinkedHashMap<String, Tag>();
		entries.put("name", new StringTag("name", "detect me"));
		entries.put("data", new LongArrayTag("data", new long[] { 1L, 2L, 3L }));
		ROOT = new CompoundTag("", entries);
	}

	@Test
	void detectsEveryCompressionWhenReading() throws Exception {
		for (final NBTCompression compression : NBTCompression.values()) {
			for (final Tag tag : new Tag[] { ROOT, new IntTag("x", 1), new LongArrayTag("l", new long[0]) }) {
				try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(write(tag, compression)))) {
					assertEquals(compression, in.getCompression(), compression + " " + tag);
					assertEquals(tag, in.readTag(), compression + " " + tag);
				}
			}
		}
	}

	@Test
	void detectsZlibAtEveryCompressionLevel() throws Exception {
		for (int level = Deflater.NO_COMPRESSION; level <= Deflater.BEST_COMPRESSION; level++) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (NBTOutputStream out = new NBTOutputStream(
					new DeflaterOutputStream(bytes, new Deflater(level)), NBTCompression.NONE)) {
				out.writeTag(ROOT);
			}
			try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
				assertEquals(NBTCompression.DEFLATE, in.getCompression(), "level " + level);
				assertEquals(ROOT, in.readTag(), "level " + level);
			}
		}
	}

	@Test
	void detectRecognizesHeadersAndRejectsOtherBytes() {
		assertEquals(Optional.of(NBTCompression.NONE), NBTCompression.detect(new byte[] { 10 }));
		assertEquals(Optional.of(NBTCompression.NONE), NBTCompression.detect(new byte[] { 0 }));
		assertEquals(Optional.of(NBTCompression.GZIP), NBTCompression.detect(new byte[] { 0x1F, (byte) 0x8B }));
		assertEquals(Optional.of(NBTCompression.DEFLATE), NBTCompression.detect(new byte[] { 0x78, 0x01 }));
		assertEquals(Optional.of(NBTCompression.DEFLATE), NBTCompression.detect(new byte[] { 0x78, 0x5E }));
		assertEquals(Optional.of(NBTCompression.DEFLATE), NBTCompression.detect(new byte[] { 0x78, (byte) 0x9C }));
		assertEquals(Optional.of(NBTCompression.DEFLATE), NBTCompression.detect(new byte[] { 0x78, (byte) 0xDA }));
		assertEquals(Optional.of(NBTCompression.LZ4), NBTCompression.detect("LZ4Block".getBytes()));

		assertEquals(Optional.empty(), NBTCompression.detect(new byte[0]));
		assertEquals(Optional.empty(), NBTCompression.detect(new byte[] { 0x1F }));
		assertEquals(Optional.empty(), NBTCompression.detect(new byte[] { 0x1F, 0x00 }));
		assertEquals(Optional.empty(), NBTCompression.detect(new byte[] { 0x78, 0x00 }));
		assertEquals(Optional.empty(), NBTCompression.detect("LZ4Bloc".getBytes()));
		assertEquals(Optional.empty(), NBTCompression.detect(new byte[] { (byte) 0xFF, 0x00 }));
	}

	@Test
	void rejectsEmptyAndUnrecognizedStreamsAndClosesThem() {
		final boolean[] closed = { false };
		final ByteArrayInputStream unknown = new ByteArrayInputStream(new byte[] { (byte) 0xFF, 1, 2 }) {
			@Override
			public void close() { closed[0] = true; }
		};
		final IOException e = assertThrows(IOException.class, () -> new NBTInputStream(unknown));
		assertEquals("[JNBT] Unrecognized NBT compression: the stream starts with 0xFF.", e.getMessage());
		assertTrue(closed[0]);

		assertThrows(EOFException.class, () -> new NBTInputStream(new ByteArrayInputStream(new byte[0])));
	}

	@Test
	void explicitCompressionAndDataInputStreamsReportTheirCompression() throws Exception {
		final byte[] raw = write(ROOT, NBTCompression.NONE);
		try (NBTInputStream in = new NBTInputStream(new DataInputStream(new ByteArrayInputStream(raw)))) {
			assertEquals(NBTCompression.NONE, in.getCompression());
		}
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(write(ROOT, NBTCompression.LZ4)),
				NBTCompression.LZ4)) {
			assertEquals(NBTCompression.LZ4, in.getCompression());
		}
		// A one-byte stream is still detected; reading it then fails as truncated input.
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(Arrays.copyOf(raw, 1)))) {
			assertEquals(NBTCompression.NONE, in.getCompression());
			assertThrows(EOFException.class, in::readTag);
		}
	}

	private static byte[] write(final Tag tag, final NBTCompression compression) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(bytes, compression)) {
			out.writeTag(tag);
		}
		return bytes.toByteArray();
	}
}
