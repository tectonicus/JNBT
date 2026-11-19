package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class BedrockFormatTest {
	private static final CompoundTag EVERY_TYPE = CompoundTag.builder()
			.putByte("byte", (byte) -7)
			.putShort("short", (short) 0x1234)
			.putInt("int", 0x12345678)
			.putLong("long", 0x0102030405060708L)
			.putFloat("float", 1.25f)
			.putDouble("double", -3.5d)
			.putByteArray("bytes", new byte[] { 0, -1 })
			.putString("string", "a\u0000é🌍")
			.put("list", ListTag.builder(IntTag.class).add(new IntTag("", 2)).add(new IntTag("", 3)).build())
			.put("emptyList", ListTag.builder(EndTag.class).build())
			.putCompound("compound", c -> c.putString("inside", "compound"))
			.putIntArray("ints", new int[] { -1, 0, 1 })
			.putLongArray("longs", new long[] { -1L, 0L, 1L })
			.build();

	@Test
	void writesNumbersLittleEndian() throws Exception {
		assertArrayEquals(new byte[] { 3, 1, 0, 'x', 4, 3, 2, 1 },
				write(new IntTag("x", 0x01020304), NBTFormat.BEDROCK));
		assertArrayEquals(new byte[] { 2, 1, 0, 's', 0x34, 0x12 },
				write(new ShortTag("s", (short) 0x1234), NBTFormat.BEDROCK));
		assertArrayEquals(new byte[] { 4, 1, 0, 'l', 8, 7, 6, 5, 4, 3, 2, 1 },
				write(new LongTag("l", 0x0102030405060708L), NBTFormat.BEDROCK));
		// 1.0f is 0x3F800000.
		assertArrayEquals(new byte[] { 5, 1, 0, 'f', 0, 0, (byte) 0x80, 0x3F },
				write(new FloatTag("f", 1.0f), NBTFormat.BEDROCK));
		assertArrayEquals(new byte[] { 11, 1, 0, 'i', 2, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0 },
				write(new IntArrayTag("i", new int[] { 1, 2 }), NBTFormat.BEDROCK));
		// Element type 3 (TAG_Int), then the length 1, then the element.
		assertArrayEquals(new byte[] { 9, 1, 0, 'l', 3, 1, 0, 0, 0, 7, 0, 0, 0 },
				write(new ListTag("l", IntTag.class, List.<Tag>of(new IntTag("", 7))), NBTFormat.BEDROCK));
	}

	@Test
	void writesStringsAsStandardUtf8() throws Exception {
		final String text = "a\u0000é🌍";
		final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
		final ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(new byte[] { 8, 1, 0, 's', (byte) utf8.length, 0 });
		expected.write(utf8);
		final StringTag tag = new StringTag("s", text);
		assertArrayEquals(expected.toByteArray(), write(tag, NBTFormat.BEDROCK));
		assertEquals(tag, read(expected.toByteArray(), NBTFormat.BEDROCK));
	}

	@Test
	void roundTripsEveryTagTypeWithEveryCompression() throws Exception {
		for (final NBTCompression compression : NBTCompression.values()) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (NBTOutputStream out = new NBTOutputStream(bytes, compression, NBTFormat.BEDROCK)) {
				assertEquals(NBTFormat.BEDROCK, out.getFormat());
				out.writeTag(EVERY_TYPE);
			}
			try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()),
					NBTFormat.BEDROCK)) {
				assertEquals(compression, in.getCompression());
				assertEquals(NBTFormat.BEDROCK, in.getFormat());
				assertEquals(EVERY_TYPE, in.readTag(), compression.toString());
			}
		}
	}

	@Test
	void javaAndBedrockBytesDifferAndJavaStaysTheDefault() throws Exception {
		final byte[] java = write(EVERY_TYPE, NBTFormat.JAVA);
		final ByteArrayOutputStream defaults = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(defaults, NBTCompression.NONE)) {
			assertEquals(NBTFormat.JAVA, out.getFormat());
			out.writeTag(EVERY_TYPE);
		}
		assertArrayEquals(java, defaults.toByteArray());
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(java))) {
			assertEquals(NBTFormat.JAVA, in.getFormat());
		}
		final byte[] bedrock = write(EVERY_TYPE, NBTFormat.BEDROCK);
		assertEquals(EVERY_TYPE, read(bedrock, NBTFormat.BEDROCK));
		assertEquals(false, java.length == bedrock.length && java.equals(bedrock));
	}

	@Test
	void rejectsInvalidStrings() {
		// 0xFF never appears in UTF-8.
		assertThrows(IOException.class,
				() -> read(new byte[] { 8, 0, 0, 1, 0, (byte) 0xFF }, NBTFormat.BEDROCK));
		// An unpaired surrogate cannot be encoded in standard UTF-8.
		assertThrows(IOException.class,
				() -> write(new StringTag("s", "\uD800"), NBTFormat.BEDROCK));
		assertThrows(IOException.class,
				() -> write(new StringTag("s", "é".repeat(32768)), NBTFormat.BEDROCK));
	}

	@Test
	void levelDatRoundTripsWithItsHeader() throws Exception {
		final BedrockLevelDat levelDat = new BedrockLevelDat(10,
				CompoundTag.builder().putString("LevelName", "My World").putInt("StorageVersion", 10).build());
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		levelDat.write(bytes);
		final byte[] file = bytes.toByteArray();
		final int length = file.length - 8;
		assertArrayEquals(new byte[] { 10, 0, 0, 0, (byte) length, (byte) (length >> 8), 0, 0 },
				java.util.Arrays.copyOf(file, 8));
		assertEquals(levelDat, BedrockLevelDat.read(new ByteArrayInputStream(file)));
	}

	@Test
	void levelDatRejectsBadHeadersAndContent() throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new BedrockLevelDat(10, CompoundTag.builder().putInt("x", 1).build()).write(bytes);
		final byte[] good = bytes.toByteArray();

		final byte[] tooLong = good.clone();
		tooLong[4]++;
		assertThrows(EOFException.class, () -> BedrockLevelDat.read(new ByteArrayInputStream(tooLong)));

		final byte[] tooShort = good.clone();
		tooShort[4]--;
		assertThrows(IOException.class, () -> BedrockLevelDat.read(new ByteArrayInputStream(tooShort)));

		final byte[] negative = good.clone();
		negative[7] = (byte) 0x80;
		assertThrows(IOException.class, () -> BedrockLevelDat.read(new ByteArrayInputStream(negative)));

		final ByteArrayOutputStream intRoot = new ByteArrayOutputStream();
		intRoot.write(new byte[] { 10, 0, 0, 0, 8, 0, 0, 0 });
		intRoot.write(write(new IntTag("x", 1), NBTFormat.BEDROCK));
		assertThrows(IOException.class,
				() -> BedrockLevelDat.read(new ByteArrayInputStream(intRoot.toByteArray())));

		assertThrows(EOFException.class, () -> BedrockLevelDat.read(new ByteArrayInputStream(new byte[3])));
	}

	/**
	 * Reads a real Bedrock level.dat when the JNBT_BEDROCK_LEVEL_DAT environment
	 * variable points to one, and checks that writing it back reproduces the file.
	 */
	@Test
	void readsARealLevelDatWhenOneIsGiven() throws Exception {
		final String location = System.getenv("JNBT_BEDROCK_LEVEL_DAT");
		assumeTrue(location != null && !location.isBlank(), "JNBT_BEDROCK_LEVEL_DAT is not set");
		final Path file = Path.of(location);
		final BedrockLevelDat levelDat = BedrockLevelDat.read(file);
		levelDat.data().getString("LevelName");
		final ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
		levelDat.write(rewritten);
		assertArrayEquals(Files.readAllBytes(file), rewritten.toByteArray());
	}

	private static byte[] write(final Tag tag, final NBTFormat format) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(bytes, NBTCompression.NONE, format)) {
			out.writeTag(tag);
		}
		return bytes.toByteArray();
	}

	private static Tag read(final byte[] data, final NBTFormat format) throws IOException {
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(data), NBTCompression.NONE,
				NBTReadLimits.DEFAULT, format)) {
			return in.readTag();
		}
	}
}
