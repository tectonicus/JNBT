package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class SNBTTest {
	@Test
	void writesNumbersWithTypeSuffixes() {
		assertEquals("-7b", SNBT.toString(new ByteTag("", (byte) -7)));
		assertEquals("1234s", SNBT.toString(new ShortTag("", (short) 1234)));
		assertEquals("123456", SNBT.toString(new IntTag("", 123456)));
		assertEquals("1234567890123L", SNBT.toString(new LongTag("", 1234567890123L)));
		assertEquals("1.5f", SNBT.toString(new FloatTag("", 1.5f)));
		assertEquals("1.0E10f", SNBT.toString(new FloatTag("", 1e10f)));
		assertEquals("-3.5d", SNBT.toString(new DoubleTag("", -3.5d)));
		assertEquals("64.0d", SNBT.toString(new DoubleTag("", 64d)));
	}

	@Test
	void writesArraysWithTheirPrefixes() {
		assertEquals("[B;1b,-1b]", SNBT.toString(new ByteArrayTag("", new byte[] { 1, -1 })));
		assertEquals("[I;1,2,3]", SNBT.toString(new IntArrayTag("", new int[] { 1, 2, 3 })));
		assertEquals("[L;-1L,0L]", SNBT.toString(new LongArrayTag("", new long[] { -1L, 0L })));
		assertEquals("[B;]", SNBT.toString(new ByteArrayTag("", new byte[0])));
		assertEquals("[I;]", SNBT.toString(new IntArrayTag("", new int[0])));
		assertEquals("[L;]", SNBT.toString(new LongArrayTag("", new long[0])));
	}

	@Test
	void quotesStringsAndEscapesOnlyBackslashAndDoubleQuote() {
		assertEquals("\"minecraft:stone\"", SNBT.toString(new StringTag("", "minecraft:stone")));
		assertEquals("\"\"", SNBT.toString(new StringTag("", "")));
		assertEquals("\"say \\\"hi\\\" \\\\ it's\"", SNBT.toString(new StringTag("", "say \"hi\" \\ it's")));
		assertEquals("\"two\nlines é🌍\"", SNBT.toString(new StringTag("", "two\nlines é🌍")));
	}

	@Test
	void quotesOnlyKeysThatNeedIt() {
		final CompoundTag tag = CompoundTag.builder()
				.putInt("simple_Key-1.0+", 1)
				.putInt("has space", 2)
				.putInt("minecraft:id", 3)
				.putInt("", 4)
				.putInt("quote\"key", 5)
				.build();
		assertEquals("{simple_Key-1.0+:1,\"has space\":2,\"minecraft:id\":3,\"\":4,\"quote\\\"key\":5}",
				SNBT.toString(tag));
	}

	@Test
	void writesCompactNestedTagsAndDropsTheRootName() {
		final CompoundTag item = CompoundTag.builder("ignored root name")
				.putByte("Count", (byte) 1)
				.putString("id", "minecraft:stone")
				.put("Pos", ListTag.builder(DoubleTag.class)
						.add(new DoubleTag("", 1.5)).add(new DoubleTag("", 64)).add(new DoubleTag("", 0)).build())
				.putCompound("tag", tag -> tag.putCompound("display", display -> display.putString("Name", "Rock")))
				.put("Empty", ListTag.builder(EndTag.class).build())
				.putCompound("Nothing", nothing -> { })
				.build();
		assertEquals("{Count:1b,id:\"minecraft:stone\",Pos:[1.5d,64.0d,0.0d],"
				+ "tag:{display:{Name:\"Rock\"}},Empty:[],Nothing:{}}", SNBT.toString(item));
	}

	@Test
	void writesIndentedOutput() {
		final CompoundTag root = CompoundTag.builder()
				.putString("name", "Example")
				.put("Pos", ListTag.builder(DoubleTag.class).add(new DoubleTag("", 1)).add(new DoubleTag("", 2)).build())
				.putIntArray("heights", new int[] { 64, 65 })
				.put("Items", ListTag.builder(CompoundTag.class)
						.add(CompoundTag.builder().putString("id", "a").build())
						.add(CompoundTag.builder().putString("id", "b").build())
						.build())
				.putCompound("Empty", empty -> { })
				.put("NoItems", ListTag.builder(CompoundTag.class).build())
				.build();
		assertEquals(String.join("\n",
				"{",
				"    name: \"Example\",",
				"    Pos: [1.0d, 2.0d],",
				"    heights: [I;64, 65],",
				"    Items: [",
				"        {",
				"            id: \"a\"",
				"        },",
				"        {",
				"            id: \"b\"",
				"        }",
				"    ],",
				"    Empty: {},",
				"    NoItems: []",
				"}"), SNBT.toPrettyString(root));
	}

	@Test
	void writesListsOfLists() {
		final ListTag inner = ListTag.builder(IntTag.class).add(new IntTag("", 1)).add(new IntTag("", 2)).build();
		final ListTag outer = new ListTag("", ListTag.class, List.<Tag>of(inner, inner));
		assertEquals("[[1,2],[1,2]]", SNBT.toString(outer));
		assertEquals("[\n    [1, 2],\n    [1, 2]\n]", SNBT.toPrettyString(outer));
	}

	@Test
	void rejectsEndTag() {
		assertThrows(IllegalArgumentException.class, () -> SNBT.toString(new EndTag()));
		assertThrows(IllegalArgumentException.class, () -> SNBT.toPrettyString(new EndTag()));
	}
}
