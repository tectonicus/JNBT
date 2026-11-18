package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NBTSerializationTest {
	private static final byte[] INT_FIXTURE = new byte[] {
			3, 0, 1, 'x', 1, 2, 3, 4
	};
	private static final byte[] COMPOUND_FIXTURE = new byte[] {
			10, 0, 0,
			3, 0, 1, 'x', 1, 2, 3, 4,
			0
	};

	@Test
	void usesModifiedUtf8ForNamesAndStrings() throws Exception {
		// Minecraft writes NBT strings with DataOutput.writeUTF: U+0000 takes two
		// bytes and each half of a surrogate pair takes three.
		final String text = "a\u0000🌍";
		final ByteArrayOutputStream expected = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(expected);
		out.writeByte(NBTConstants.TYPE_STRING);
		out.writeUTF(text);
		out.writeUTF(text);
		final byte[] fixture = expected.toByteArray();

		final StringTag tag = new StringTag(text, text);
		assertArrayEquals(fixture, write(tag, NBTOutputStream.Compression.None));
		assertEquals(tag, read(fixture, NBTInputStream.Compression.None));
	}

	@Test
	void rejectsMalformedModifiedUtf8() {
		assertThrows(IOException.class, () -> read(new byte[] { 8, 0, 0, 0, 1, (byte) 0xFF },
				NBTInputStream.Compression.None));
	}

	@Test
	void preservesCompoundEntryOrderWhenReading() throws Exception {
		final Map<String, Tag> tags = new LinkedHashMap<String, Tag>();
		for (final String key : new String[] { "zeta", "alpha", "Mid", "b", "yy", "c1" }) {
			tags.put(key, new IntTag(key, key.length()));
		}
		final byte[] data = write(new CompoundTag("root", tags), NBTOutputStream.Compression.None);
		final CompoundTag actual = (CompoundTag) read(data, NBTInputStream.Compression.None);
		assertEquals(new ArrayList<String>(tags.keySet()),
				new ArrayList<String>(actual.getValue().keySet()));
		assertArrayEquals(data, write(actual, NBTOutputStream.Compression.None));
	}

	@Test
	void byteArrayToStringShowsEachByteAsTwoHexDigits() {
		assertEquals("TAG_Byte_Array(\"b\"): 80 FF 01 ",
				new ByteArrayTag("b", new byte[] { (byte) 0x80, (byte) 0xFF, 1 }).toString());
	}

	@Test
	void rejectsNullNamesAndValuesAtConstruction() {
		assertThrows(NullPointerException.class, () -> new IntTag(null, 1));
		assertThrows(NullPointerException.class, () -> new StringTag("s", null));
		assertThrows(NullPointerException.class, () -> new ByteArrayTag("b", null));
		assertThrows(NullPointerException.class, () -> new IntArrayTag("i", null));
		assertThrows(NullPointerException.class, () -> new LongArrayTag("l", null));
	}

	@Test
	void roundTripsEveryTagType() throws Exception {
		final Map<String, Tag> tags = new LinkedHashMap<String, Tag>();
		tags.put("byte", new ByteTag("byte", (byte) -7));
		tags.put("short", new ShortTag("short", (short) 1234));
		tags.put("int", new IntTag("int", 123456));
		tags.put("long", new LongTag("long", 1234567890123L));
		tags.put("float", new FloatTag("float", 1.25f));
		tags.put("double", new DoubleTag("double", -3.5d));
		tags.put("byteArray", new ByteArrayTag("byteArray", new byte[] { 0, -1 }));
		tags.put("string", new StringTag("string", "hello 🌍"));
		tags.put("list", new ListTag("list", IntTag.class,
				Arrays.<Tag>asList(new IntTag("", 2), new IntTag("", 3))));
		final Map<String, Tag> nested = new LinkedHashMap<String, Tag>();
		nested.put("inside", new StringTag("inside", "compound"));
		tags.put("compound", new CompoundTag("compound", nested));
		tags.put("intArray", new IntArrayTag("intArray", new int[] { -1, 0, 1 }));
		tags.put("longArray", new LongArrayTag("longArray", new long[] { -1L, 0L, 1L }));
		tags.put("emptyEndList", new ListTag("emptyEndList", EndTag.class,
				new ArrayList<Tag>()));

		final CompoundTag root = new CompoundTag("root", tags);
		final CompoundTag actual = (CompoundTag) read(
				write(root, NBTOutputStream.Compression.None),
				NBTInputStream.Compression.None);
		assertEquals(root, actual);
		assertEquals(IntTag.class, ((ListTag) actual.getValue().get("list")).getType());
		assertEquals(EndTag.class,
				((ListTag) actual.getValue().get("emptyEndList")).getType());
	}

	@Test
	void writesAndReadsByteForByteFixtures() throws Exception {
		final IntTag intTag = new IntTag("x", 0x01020304);
		assertArrayEquals(INT_FIXTURE, write(intTag, NBTOutputStream.Compression.None));
		assertEquals(intTag, read(INT_FIXTURE, NBTInputStream.Compression.None));

		final Map<String, Tag> child = new LinkedHashMap<String, Tag>();
		child.put("x", intTag);
		final CompoundTag compound = new CompoundTag("", child);
		assertArrayEquals(COMPOUND_FIXTURE,
				write(compound, NBTOutputStream.Compression.None));
		assertEquals(compound, read(COMPOUND_FIXTURE, NBTInputStream.Compression.None));
	}

	@Test
	void readsAndWritesEveryCompressionMode() throws Exception {
		final Tag tag = new StringTag("message", "compressed text");
		final NBTOutputStream.Compression[] outputModes = NBTOutputStream.Compression.values();
		final NBTInputStream.Compression[] inputModes = NBTInputStream.Compression.values();
		assertEquals(outputModes.length, inputModes.length);
		for (int i = 0; i < outputModes.length; i++) {
			assertEquals(tag, read(write(tag, outputModes[i]), inputModes[i]));
		}
	}

	@Test
	void arrayTagsCopyAtConstructionAndAccess() {
		final byte[] bytes = { 1, 2 };
		final ByteArrayTag byteTag = new ByteArrayTag("bytes", bytes);
		bytes[0] = 9;
		final byte[] returnedBytes = byteTag.getValue();
		returnedBytes[1] = 9;
		assertArrayEquals(new byte[] { 1, 2 }, byteTag.getValue());

		final int[] ints = { 3, 4 };
		final IntArrayTag intTag = new IntArrayTag("ints", ints);
		ints[0] = 9;
		intTag.getValue()[1] = 9;
		assertArrayEquals(new int[] { 3, 4 }, intTag.getValue());

		final long[] longs = { 5L, 6L };
		final LongArrayTag longTag = new LongArrayTag("longs", longs);
		longs[0] = 9L;
		longTag.getValue()[1] = 9L;
		assertArrayEquals(new long[] { 5L, 6L }, longTag.getValue());
	}

	@Test
	void collectionTagsCopyInputsAndExposeReadOnlyValues() {
		final List<Tag> sourceList = new ArrayList<Tag>();
		sourceList.add(new IntTag("", 1));
		final ListTag listTag = new ListTag("items", IntTag.class, sourceList);
		sourceList.add(new IntTag("", 2));
		assertEquals(1, listTag.getValue().size());
		assertThrows(UnsupportedOperationException.class,
				() -> listTag.getValue().add(new IntTag("", 3)));

		final Map<String, Tag> sourceMap = new LinkedHashMap<String, Tag>();
		sourceMap.put("first", new IntTag("first", 1));
		final CompoundTag compound = new CompoundTag("root", sourceMap);
		sourceMap.put("second", new IntTag("second", 2));
		assertEquals(1, compound.getValue().size());
		assertThrows(UnsupportedOperationException.class,
				() -> compound.getValue().put("third", new IntTag("third", 3)));
	}

	@Test
	void rejectsInvalidListAndCompoundConstruction() {
		assertThrows(IllegalArgumentException.class, () -> new ListTag("bad",
				IntTag.class, Arrays.<Tag>asList(new StringTag("", "wrong"))));
		assertThrows(IllegalArgumentException.class, () -> new ListTag("bad",
				EndTag.class, Arrays.<Tag>asList(new EndTag())));
		assertThrows(IllegalArgumentException.class, () -> new ListTag("bad",
				Tag.class, new ArrayList<Tag>()));
		assertThrows(IllegalArgumentException.class, () -> new CompoundTag("bad",
				Map.of("key", new IntTag("different", 1))));
	}

	@Test
	void rejectsMalformedAndTruncatedInput() {
		assertThrows(IOException.class, () -> read(new byte[] { 99 },
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(new byte[] { 3, 0 },
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(new byte[] { 3, 0, 1, 'x', 1, 2 },
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(new byte[] { 0 },
				NBTInputStream.Compression.None));
	}

	@Test
	void rejectsOverlongUtf8StringsAndNamesWhenWriting() {
		final String tooLong = "a".repeat(65536);
		assertThrows(IOException.class, () -> write(new StringTag("s", tooLong),
				NBTOutputStream.Compression.None));
		assertThrows(IOException.class, () -> write(new ByteTag(tooLong, (byte) 1),
				NBTOutputStream.Compression.None));
		assertThrows(IOException.class, () -> write(new StringTag("s", "🌍".repeat(16385)),
				NBTOutputStream.Compression.None));
	}

	@Test
	void rejectsNegativeLengthsAndConfiguredSizeLimits() throws Exception {
		assertThrows(IOException.class, () -> read(tagWithLength(7, -1),
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(tagWithLength(9, -1),
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(tagWithLength(11, -1),
				NBTInputStream.Compression.None));
		assertThrows(IOException.class, () -> read(tagWithLength(12, -1),
				NBTInputStream.Compression.None));

		final byte[] bytesArray = tagWithLength(7, 2, (byte) 1, (byte) 2);
		assertThrows(IOException.class, () -> read(bytesArray, NBTInputStream.Compression.None,
				new NBTReadLimits(5, 20, 1, 10)));
		final byte[] list = listFixture(1);
		assertThrows(IOException.class, () -> read(list, NBTInputStream.Compression.None,
				new NBTReadLimits(5, 20, 10, 0)));
		assertThrows(IOException.class, () -> read(stringFixture("long"),
				NBTInputStream.Compression.None,
				new NBTReadLimits(5, 3, 10, 10)));
	}

	@Test
	void rejectsDepthBeyondConfiguredLimit() throws Exception {
		final Map<String, Tag> inner = new LinkedHashMap<String, Tag>();
		inner.put("leaf", new IntTag("leaf", 1));
		final Map<String, Tag> outer = new LinkedHashMap<String, Tag>();
		outer.put("nested", new CompoundTag("nested", inner));
		final byte[] data = write(new CompoundTag("root", outer),
				NBTOutputStream.Compression.None);
		assertThrows(IOException.class, () -> read(data, NBTInputStream.Compression.None,
				new NBTReadLimits(1, 100, 100, 100)));
		assertEquals(new CompoundTag("root", outer), read(data,
				NBTInputStream.Compression.None, new NBTReadLimits(2, 100, 100, 100)));
	}

	private static byte[] write(Tag tag, NBTOutputStream.Compression compression)
			throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream output = new NBTOutputStream(bytes, compression)) {
			output.writeTag(tag);
		}
		return bytes.toByteArray();
	}

	private static Tag read(byte[] data, NBTInputStream.Compression compression)
			throws IOException {
		return read(data, compression, NBTReadLimits.DEFAULT);
	}

	private static Tag read(byte[] data, NBTInputStream.Compression compression,
			NBTReadLimits limits) throws IOException {
		try (NBTInputStream input = new NBTInputStream(new ByteArrayInputStream(data),
				compression, limits)) {
			return input.readTag();
		}
	}

	private static byte[] tagWithLength(int type, int length, byte... payload)
			throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(type);
		out.writeShort(0);
		if (type == 7 || type == 11 || type == 12 || type == 9) {
			if (type == 9) { out.writeByte(1); }
			out.writeInt(length);
		}
		out.write(payload);
		return bytes.toByteArray();
	}

	private static byte[] listFixture(int size) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(9); out.writeShort(0); out.writeByte(3); out.writeInt(size);
		for (int i = 0; i < size; i++) { out.writeInt(i); }
		return bytes.toByteArray();
	}

	private static byte[] stringFixture(String value) throws IOException {
		final byte[] text = value.getBytes(NBTConstants.CHARSET);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(8); out.writeShort(0); out.writeShort(text.length); out.write(text);
		return bytes.toByteArray();
	}
}
