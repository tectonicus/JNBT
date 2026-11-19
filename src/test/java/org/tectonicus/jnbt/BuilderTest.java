package org.tectonicus.jnbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BuilderTest {
	@Test
	void compoundBuilderMatchesTheMapConstructor() {
		final Map<String, Tag> inner = new LinkedHashMap<String, Tag>();
		inner.put("x", new IntTag("x", 1));
		final Map<String, Tag> entries = new LinkedHashMap<String, Tag>();
		entries.put("b", new ByteTag("b", (byte) 1));
		entries.put("flag", new ByteTag("flag", (byte) 1));
		entries.put("s", new ShortTag("s", (short) 2));
		entries.put("i", new IntTag("i", 3));
		entries.put("l", new LongTag("l", 4L));
		entries.put("f", new FloatTag("f", 5f));
		entries.put("d", new DoubleTag("d", 6d));
		entries.put("str", new StringTag("str", "seven"));
		entries.put("bytes", new ByteArrayTag("bytes", new byte[] { 8 }));
		entries.put("ints", new IntArrayTag("ints", new int[] { 9 }));
		entries.put("longs", new LongArrayTag("longs", new long[] { 10L }));
		entries.put("inner", new CompoundTag("inner", inner));
		entries.put("tag", new IntTag("tag", 11));

		final CompoundTag built = CompoundTag.builder("root")
				.putByte("b", (byte) 1)
				.putBoolean("flag", true)
				.putShort("s", (short) 2)
				.putInt("i", 3)
				.putLong("l", 4L)
				.putFloat("f", 5f)
				.putDouble("d", 6d)
				.putString("str", "seven")
				.putByteArray("bytes", new byte[] { 8 })
				.putIntArray("ints", new int[] { 9 })
				.putLongArray("longs", new long[] { 10L })
				.putCompound("inner", c -> c.putInt("x", 1))
				.put(new IntTag("tag", 11))
				.build();
		assertEquals(new CompoundTag("root", entries), built);
		assertEquals(Arrays.asList(entries.keySet().toArray()), Arrays.asList(built.getValue().keySet().toArray()));
	}

	@Test
	void putUnderANameRenamesTagsBuiltElsewhere() {
		final CompoundTag pos = CompoundTag.builder().putInt("x", 1).build();
		final ListTag motion = ListTag.builder(DoubleTag.class)
				.add(new DoubleTag("", 0.5)).add(new DoubleTag("", -1.0)).build();
		final CompoundTag entity = CompoundTag.builder()
				.put("Pos", pos)
				.put("Motion", motion)
				.build();
		assertEquals("Pos", entity.getCompound("Pos").getName());
		assertEquals(1, entity.getCompound("Pos").getInt("x"));
		assertEquals("Motion", entity.getList("Motion").getName());
		assertEquals(-1.0, entity.getList("Motion", DoubleTag.class).get(1).getValue());
		assertEquals("", pos.getName());
	}

	@Test
	void puttingANameAgainReplacesItInPlaceAndRemoveDeletes() {
		final CompoundTag tag = CompoundTag.builder()
				.putInt("a", 1).putInt("b", 2).putString("a", "replaced").remove("b").remove("missing")
				.build();
		assertEquals(List.of("a"), List.copyOf(tag.getValue().keySet()));
		assertEquals("replaced", tag.getString("a"));
	}

	@Test
	void toBuilderMakesAChangedCopy() {
		final CompoundTag original = CompoundTag.builder("level").putInt("x", 1).putInt("y", 2).build();
		final CompoundTag changed = original.toBuilder().putInt("x", 5).remove("y").putInt("z", 3).build();
		assertEquals("level", changed.getName());
		assertEquals(5, changed.getInt("x"));
		assertFalse(changed.contains("y"));
		assertEquals(3, changed.getInt("z"));
		assertEquals(1, original.getInt("x"));
		assertEquals(2, original.getInt("y"));
	}

	@Test
	void builtCompoundIsUnaffectedByLaterBuilderChanges() {
		final CompoundTag.Builder builder = CompoundTag.builder().putInt("x", 1);
		final CompoundTag first = builder.build();
		builder.putInt("y", 2);
		assertFalse(first.contains("y"));

		final int[] ints = { 1, 2 };
		final CompoundTag arrays = CompoundTag.builder().putIntArray("ints", ints).build();
		ints[0] = 9;
		assertArrayEquals(new int[] { 1, 2 }, arrays.getIntArray("ints"));
	}

	@Test
	void listBuilderDropsElementNamesAndKeepsOrder() {
		final ListTag list = ListTag.builder("items", CompoundTag.class)
				.add(CompoundTag.builder("named").putInt("n", 1).build())
				.addAll(List.of(CompoundTag.builder().putInt("n", 2).build(),
						CompoundTag.builder().putInt("n", 3).build()))
				.build();
		assertEquals("items", list.getName());
		assertEquals(CompoundTag.class, list.getType());
		assertEquals(3, list.getValue().size());
		assertEquals("", list.getValue().get(0).getName());
		for (int i = 0; i < 3; i++) {
			assertEquals(i + 1, ((CompoundTag) list.getValue().get(i)).getInt("n"));
		}
		assertEquals(0, ListTag.builder("empty", StringTag.class).build().getValue().size());
	}

	@Test
	void buildersRejectInvalidInput() {
		assertThrows(NullPointerException.class, () -> CompoundTag.builder(null));
		assertThrows(NullPointerException.class, () -> CompoundTag.builder().putString("s", null));
		assertThrows(NullPointerException.class, () -> CompoundTag.builder().put(null, new IntTag("", 1)));
		assertThrows(IllegalArgumentException.class, () -> CompoundTag.builder().put(new EndTag()));
		assertThrows(IllegalArgumentException.class, () -> CompoundTag.builder().put("end", new EndTag()));
		assertThrows(IllegalArgumentException.class, () -> new CompoundTag("", Map.of("", new EndTag())));

		assertThrows(NullPointerException.class, () -> ListTag.builder(null, IntTag.class));
		assertThrows(NullPointerException.class, () -> ListTag.builder(IntTag.class).add(null));
		assertThrows(IllegalArgumentException.class,
				() -> ListTag.builder(EndTag.class).add(new EndTag()).build());
		assertThrows(IllegalArgumentException.class, () -> ListTag.builder(Tag.class).build());
	}

	@Test
	void putReturnsTheSameTagWhenTheNameAlreadyMatches() {
		final IntTag tag = new IntTag("x", 1);
		assertSame(tag, CompoundTag.builder().put("x", tag).build().getValue().get("x"));
	}

	@Test
	void builtTagsRoundTrip() throws Exception {
		final CompoundTag root = CompoundTag.builder()
				.putString("name", "Example")
				.putCompound("Data", data -> data
						.putInt("version", 3)
						.put("Pos", ListTag.builder(DoubleTag.class)
								.add(new DoubleTag("", 1.5)).add(new DoubleTag("", 64.0)).build()))
				.build();
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (NBTOutputStream out = new NBTOutputStream(bytes, NBTCompression.GZIP)) {
			out.writeTag(root);
		}
		try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			assertEquals(root, in.readTag());
		}
	}
}
