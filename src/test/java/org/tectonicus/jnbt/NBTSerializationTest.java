package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
		assertArrayEquals(fixture, write(tag, NBTCompression.NONE));
		assertEquals(tag, read(fixture, NBTCompression.NONE));
	}

	@Test
	void rejectsMalformedModifiedUtf8() {
		assertThrows(IOException.class, () -> read(new byte[] { 8, 0, 0, 0, 1, (byte) 0xFF },
				NBTCompression.NONE));
	}

	@Test
	void preservesCompoundEntryOrderWhenReading() throws Exception {
		final Map<String, Tag> tags = new LinkedHashMap<String, Tag>();
		for (final String key : new String[] { "zeta", "alpha", "Mid", "b", "yy", "c1" }) {
			tags.put(key, new IntTag(key, key.length()));
		}
		final byte[] data = write(new CompoundTag("root", tags), NBTCompression.NONE);
		final CompoundTag actual = (CompoundTag) read(data, NBTCompression.NONE);
		assertEquals(new ArrayList<String>(tags.keySet()),
				new ArrayList<String>(actual.getValue().keySet()));
		assertArrayEquals(data, write(actual, NBTCompression.NONE));
	}

	@Test
	void byteArrayToStringShowsEachByteAsTwoHexDigits() {
		assertEquals("TAG_Byte_Array(\"b\"): 80 FF 01",
				new ByteArrayTag("b", new byte[] { (byte) 0x80, (byte) 0xFF, 1 }).toString());
	}

	@Test
	void toStringShortensLongArraysAndIndentsNestedTags() {
		final int[] ints = new int[NBTUtils.TO_STRING_MAX_ELEMENTS + 10];
		final String text = new IntArrayTag("", ints).toString();
		assertTrue(text.startsWith("TAG_Int_Array: 0 0 "), text);
		assertTrue(text.endsWith("0 ... (10 more)"), text);

		final Map<String, Tag> child = new LinkedHashMap<String, Tag>();
		child.put("x", new IntTag("x", 1));
		final Map<String, Tag> root = new LinkedHashMap<String, Tag>();
		root.put("inner", new CompoundTag("inner", child));
		assertEquals("TAG_Compound(\"root\"): 1 entries\n{\n"
				+ "   TAG_Compound(\"inner\"): 1 entries\n   {\n"
				+ "      TAG_Int(\"x\"): 1\n   }\n}",
				new CompoundTag("root", root).toString());
	}

	@Test
	void arrayTagsReadElementsWithoutCopying() {
		final LongArrayTag longs = new LongArrayTag("l", new long[] { 7L, 8L });
		assertEquals(2, longs.length());
		assertEquals(8L, longs.get(1));
		final IntArrayTag ints = new IntArrayTag("i", new int[] { 5 });
		assertEquals(1, ints.length());
		assertEquals(5, ints.get(0));
		final ByteArrayTag bytes = new ByteArrayTag("b", new byte[] { 3, 4 });
		assertEquals(2, bytes.length());
		assertEquals((byte) 4, bytes.get(1));
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> bytes.get(2));
	}

	@Test
	void typeLookupsCoverEveryTagType() {
		for (int type = NBTConstants.TYPE_END; type <= NBTConstants.TYPE_LONG_ARRAY; type++) {
			assertEquals(type, NBTUtils.getTypeCode(NBTUtils.getTypeClass(type)));
		}
		assertEquals("TAG_Long_Array", NBTUtils.getTypeName(LongArrayTag.class));
		assertThrows(IllegalArgumentException.class, () -> NBTUtils.getTypeClass(13));
		assertThrows(IllegalArgumentException.class, () -> NBTUtils.getTypeClass(-1));
		assertThrows(IllegalArgumentException.class, () -> NBTUtils.getTypeCode(Tag.class));
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
	void listElementsAreStoredUnnamedAndRoundTrip() throws Exception {
		final Map<String, Tag> child = new LinkedHashMap<String, Tag>();
		child.put("x", new IntTag("x", 1));
		final ListTag list = new ListTag("list", CompoundTag.class,
				Arrays.<Tag>asList(new CompoundTag("named", child)));
		assertEquals("", list.getValue().get(0).getName());
		assertEquals(child, ((CompoundTag) list.getValue().get(0)).getValue());
		assertEquals(list, read(write(list, NBTCompression.NONE),
				NBTCompression.NONE));

		final ListTag ints = new ListTag("ints", IntTag.class,
				Arrays.<Tag>asList(new IntTag("a", 1), new IntTag("", 2)));
		assertEquals(new ListTag("ints", IntTag.class,
				Arrays.<Tag>asList(new IntTag("", 1), new IntTag("", 2))), ints);
	}

	@Test
	void duplicateCompoundNamesKeepLastValueAtFirstPosition() throws Exception {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(NBTConstants.TYPE_COMPOUND); out.writeUTF("");
		out.writeByte(NBTConstants.TYPE_INT); out.writeUTF("a"); out.writeInt(1);
		out.writeByte(NBTConstants.TYPE_INT); out.writeUTF("b"); out.writeInt(2);
		out.writeByte(NBTConstants.TYPE_INT); out.writeUTF("a"); out.writeInt(3);
		out.writeByte(NBTConstants.TYPE_END);
		final CompoundTag compound = (CompoundTag) read(bytes.toByteArray(),
				NBTCompression.NONE);
		assertEquals(Arrays.asList("a", "b"), new ArrayList<String>(compound.getValue().keySet()));
		assertEquals(new IntTag("a", 3), compound.getValue().get("a"));
	}

	@Test
	void tagNamesAreHeldToTheStringLimit() {
		assertThrows(IOException.class, () -> read(INT_FIXTURE, NBTCompression.NONE,
				new NBTReadLimits(5, 0, 10, 10)));
	}

	@Test
	void constructorsCloseTheStreamWhenSetupFails() {
		final boolean[] closed = { false };
		final InputStream notGzip = new ByteArrayInputStream(new byte[] { 1, 2, 3 }) {
			@Override
			public void close() { closed[0] = true; }
		};
		assertThrows(IOException.class, () -> new NBTInputStream(notGzip));
		assertTrue(closed[0]);

		closed[0] = false;
		final OutputStream failing = new OutputStream() {
			@Override
			public void write(final int b) throws IOException { throw new IOException("disk full"); }

			@Override
			public void write(final byte[] b, final int off, final int len) throws IOException {
				throw new IOException("disk full");
			}

			@Override
			public void close() { closed[0] = true; }
		};
		assertThrows(IOException.class, () -> new NBTOutputStream(failing));
		assertTrue(closed[0]);
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
				write(root, NBTCompression.NONE),
				NBTCompression.NONE);
		assertEquals(root, actual);
		assertEquals(IntTag.class, ((ListTag) actual.getValue().get("list")).getType());
		assertEquals(EndTag.class,
				((ListTag) actual.getValue().get("emptyEndList")).getType());
	}

	@Test
	void writesAndReadsByteForByteFixtures() throws Exception {
		final IntTag intTag = new IntTag("x", 0x01020304);
		assertArrayEquals(INT_FIXTURE, write(intTag, NBTCompression.NONE));
		assertEquals(intTag, read(INT_FIXTURE, NBTCompression.NONE));

		final Map<String, Tag> child = new LinkedHashMap<String, Tag>();
		child.put("x", intTag);
		final CompoundTag compound = new CompoundTag("", child);
		assertArrayEquals(COMPOUND_FIXTURE,
				write(compound, NBTCompression.NONE));
		assertEquals(compound, read(COMPOUND_FIXTURE, NBTCompression.NONE));
	}

	@Test
	void readsAndWritesEveryCompressionMode() throws Exception {
		final Tag tag = new StringTag("message", "compressed text");
		for (final NBTCompression compression : NBTCompression.values()) {
			assertEquals(tag, read(write(tag, compression), compression));
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
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(new byte[] { 3, 0 },
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(new byte[] { 3, 0, 1, 'x', 1, 2 },
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(new byte[] { 0 },
				NBTCompression.NONE));
	}

	@Test
	void rejectsOverlongUtf8StringsAndNamesWhenWriting() {
		final String tooLong = "a".repeat(65536);
		assertThrows(IOException.class, () -> write(new StringTag("s", tooLong),
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> write(new ByteTag(tooLong, (byte) 1),
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> write(new StringTag("s", "🌍".repeat(16385)),
				NBTCompression.NONE));
	}

	@Test
	void rejectsNegativeLengthsAndConfiguredSizeLimits() throws Exception {
		assertThrows(IOException.class, () -> read(tagWithLength(7, -1),
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(tagWithLength(9, -1),
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(tagWithLength(11, -1),
				NBTCompression.NONE));
		assertThrows(IOException.class, () -> read(tagWithLength(12, -1),
				NBTCompression.NONE));

		final byte[] bytesArray = tagWithLength(7, 2, (byte) 1, (byte) 2);
		assertThrows(IOException.class, () -> read(bytesArray, NBTCompression.NONE,
				new NBTReadLimits(5, 20, 1, 10)));
		final byte[] list = listFixture(1);
		assertThrows(IOException.class, () -> read(list, NBTCompression.NONE,
				new NBTReadLimits(5, 20, 10, 0)));
		assertThrows(IOException.class, () -> read(stringFixture("long"),
				NBTCompression.NONE,
				new NBTReadLimits(5, 3, 10, 10)));
	}

	@Test
	void rejectsDepthBeyondConfiguredLimit() throws Exception {
		final Map<String, Tag> inner = new LinkedHashMap<String, Tag>();
		inner.put("leaf", new IntTag("leaf", 1));
		final Map<String, Tag> outer = new LinkedHashMap<String, Tag>();
		outer.put("nested", new CompoundTag("nested", inner));
		final byte[] data = write(new CompoundTag("root", outer),
				NBTCompression.NONE);
		assertThrows(IOException.class, () -> read(data, NBTCompression.NONE,
				new NBTReadLimits(1, 100, 100, 100)));
		assertEquals(new CompoundTag("root", outer), read(data,
				NBTCompression.NONE, new NBTReadLimits(2, 100, 100, 100)));
	}

	private static byte[] write(Tag tag, NBTCompression compression)
			throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream output = new NBTOutputStream(bytes, compression)) {
			output.writeTag(tag);
		}
		return bytes.toByteArray();
	}

	private static Tag read(byte[] data, NBTCompression compression)
			throws IOException {
		return read(data, compression, NBTReadLimits.DEFAULT);
	}

	private static Tag read(byte[] data, NBTCompression compression,
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
