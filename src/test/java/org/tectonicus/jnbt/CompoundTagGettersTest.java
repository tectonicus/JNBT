package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CompoundTagGettersTest {
	private static CompoundTag compound(final String name, final Tag... tags) {
		final Map<String, Tag> map = new LinkedHashMap<String, Tag>();
		for (final Tag tag : tags) {
			map.put(tag.getName(), tag);
		}
		return new CompoundTag(name, map);
	}

	private static final CompoundTag LEVEL = compound("Level",
			new ByteTag("byte", (byte) -1),
			new ShortTag("short", (short) 2),
			new IntTag("int", 3),
			new LongTag("long", 4L),
			new FloatTag("float", 5.5f),
			new DoubleTag("double", 6.5d),
			new StringTag("string", "text"),
			new ByteArrayTag("bytes", new byte[] { 1, 2 }),
			new IntArrayTag("ints", new int[] { 3, 4 }),
			new LongArrayTag("longs", new long[] { 5L, 6L }),
			compound("inner", new IntTag("x", 7)),
			new ListTag("sections", CompoundTag.class,
					Arrays.<Tag>asList(compound("", new IntTag("y", 8)))),
			new ListTag("empty", EndTag.class, List.of()));

	@Test
	void typedGettersReturnValues() {
		assertEquals((byte) -1, LEVEL.getByte("byte"));
		assertEquals((short) 2, LEVEL.getShort("short"));
		assertEquals(3, LEVEL.getInt("int"));
		assertEquals(4L, LEVEL.getLong("long"));
		assertEquals(5.5f, LEVEL.getFloat("float"));
		assertEquals(6.5d, LEVEL.getDouble("double"));
		assertEquals("text", LEVEL.getString("string"));
		assertArrayEquals(new byte[] { 1, 2 }, LEVEL.getByteArray("bytes"));
		assertArrayEquals(new int[] { 3, 4 }, LEVEL.getIntArray("ints"));
		assertArrayEquals(new long[] { 5L, 6L }, LEVEL.getLongArray("longs"));
		assertEquals(7, LEVEL.getCompound("inner").getInt("x"));
		assertEquals(CompoundTag.class, LEVEL.getList("sections").getType());
		assertEquals(6L, LEVEL.get("longs", LongArrayTag.class).get(1));
	}

	@Test
	void typedListReturnsElementsOfThatType() {
		final List<CompoundTag> sections = LEVEL.getList("sections", CompoundTag.class);
		assertEquals(8, sections.get(0).getInt("y"));
		assertTrue(LEVEL.getList("empty", CompoundTag.class).isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> sections.add(compound("")));

		final ClassCastException e = assertThrows(ClassCastException.class,
				() -> LEVEL.getList("sections", IntTag.class));
		assertEquals("[JNBT] \"sections\" in TAG_Compound(\"Level\") is a list of TAG_Compound, not TAG_Int.",
				e.getMessage());
	}

	@Test
	void missingEntriesThrowOrAreEmpty() {
		final NoSuchElementException e = assertThrows(NoSuchElementException.class,
				() -> LEVEL.getInt("missing"));
		assertEquals("[JNBT] \"missing\" in TAG_Compound(\"Level\") does not exist.", e.getMessage());
		assertEquals(Optional.empty(), LEVEL.find("missing", IntTag.class));
		assertEquals(Optional.of(new IntTag("int", 3)), LEVEL.find("int", IntTag.class));
	}

	@Test
	void wrongTypesThrowAClearError() {
		final ClassCastException e = assertThrows(ClassCastException.class,
				() -> LEVEL.getInt("string"));
		assertEquals("[JNBT] \"string\" in TAG_Compound(\"Level\") is TAG_String, not TAG_Int.",
				e.getMessage());
		assertThrows(ClassCastException.class, () -> LEVEL.find("string", IntTag.class));
		assertThrows(ClassCastException.class, () -> LEVEL.getCompound("sections"));
	}

	@Test
	void containsChecksNameAndType() {
		assertTrue(LEVEL.contains("int"));
		assertFalse(LEVEL.contains("missing"));
		assertTrue(LEVEL.contains("int", IntTag.class));
		assertFalse(LEVEL.contains("int", LongTag.class));
		assertFalse(LEVEL.contains("missing", IntTag.class));
	}
}
