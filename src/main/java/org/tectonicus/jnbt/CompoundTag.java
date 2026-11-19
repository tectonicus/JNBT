package org.tectonicus.jnbt;

//@formatter:off

/*
 * JNBT License
 * 
 * Copyright (c) 2010 Graham Edgecombe
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *       
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *       
 *     * Neither the name of the JNBT team nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. 
 */

//@formatter:on

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The <code>TAG_Compound</code> tag.
 * 
 * @author Graham Edgecombe
 * 
 */
public final class CompoundTag extends Tag {
	
	/**
	 * The value.
	 */
	private final Map<String, Tag> value;
	
	/**
	 * Creates the tag.
	 * 
	 * @param name
	 *            The name.
	 * @param value
	 *            The value.
	 */
	public CompoundTag(final String name, final Map<String, Tag> value) {
	
		super(name);
		if (value == null) {
			throw new NullPointerException("Compound value is required.");
		}
		final Map<String, Tag> copy = new LinkedHashMap<String, Tag>();
		for (final Map.Entry<String, Tag> entry : value.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null
					|| !entry.getKey().equals(entry.getValue().getName())) {
				throw new IllegalArgumentException("Compound keys must match non-null child tag names.");
			}
			if (entry.getValue() instanceof EndTag) {
				throw new IllegalArgumentException("TAG_End cannot be an entry in a compound.");
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		this.value = Collections.unmodifiableMap(copy);
	}

	/**
	 * Creates a builder for an unnamed compound, as used for the root of most
	 * files, for a list element, or for one to pass to
	 * {@link Builder#put(String, Tag)}.
	 *
	 * @return The builder.
	 */
	public static Builder builder() {

		return builder("");
	}

	/**
	 * Creates a builder for a compound with the given name.
	 *
	 * @param name
	 *            The compound's name.
	 * @return The builder.
	 */
	public static Builder builder(final String name) {

		return new Builder(name);
	}

	/**
	 * Creates a builder that starts with this compound's name and entries, to
	 * make a changed copy of it.
	 *
	 * @return The builder.
	 */
	public Builder toBuilder() {

		final Builder builder = new Builder(getName());
		builder.entries.putAll(value);
		return builder;
	}

	/**
	 * Builds a {@link CompoundTag} one entry at a time. Entries keep the order
	 * they were first added in, and adding a name again replaces its entry in
	 * place.
	 */
	public static final class Builder {
		private final String name;
		private final Map<String, Tag> entries = new LinkedHashMap<String, Tag>();

		private Builder(final String name) {
			if (name == null) {
				throw new NullPointerException("Compound name is required.");
			}
			this.name = name;
		}

		/**
		 * Adds a tag as an entry under its own name.
		 *
		 * @param tag
		 *            The tag.
		 * @return This builder.
		 * @throws IllegalArgumentException
		 *             if the tag is an <code>EndTag</code>.
		 */
		public Builder put(final Tag tag) {

			if (tag instanceof EndTag) {
				throw new IllegalArgumentException("TAG_End cannot be an entry in a compound.");
			}
			entries.put(tag.getName(), tag);
			return this;
		}

		/**
		 * Adds a tag as an entry under the given name, renaming a copy of the
		 * tag if its name differs. Use this for compounds and lists made with
		 * their own builders.
		 *
		 * @param name
		 *            The entry name.
		 * @param tag
		 *            The tag.
		 * @return This builder.
		 * @throws IllegalArgumentException
		 *             if the tag is an <code>EndTag</code>.
		 */
		public Builder put(final String name, final Tag tag) {

			if (name == null) {
				throw new NullPointerException("Entry name is required.");
			}
			if (tag instanceof EndTag) {
				throw new IllegalArgumentException("TAG_End cannot be an entry in a compound.");
			}
			return put(tag.withName(name));
		}

		/**
		 * Adds a <code>TAG_Byte</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putByte(final String name, final byte value) {

			return put(new ByteTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Byte</code> entry holding 1 for <code>true</code>
		 * or 0 for <code>false</code>, as Minecraft stores booleans.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putBoolean(final String name, final boolean value) {

			return putByte(name, value ? (byte) 1 : (byte) 0);
		}

		/**
		 * Adds a <code>TAG_Short</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putShort(final String name, final short value) {

			return put(new ShortTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Int</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putInt(final String name, final int value) {

			return put(new IntTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Long</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putLong(final String name, final long value) {

			return put(new LongTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Float</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putFloat(final String name, final float value) {

			return put(new FloatTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Double</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putDouble(final String name, final double value) {

			return put(new DoubleTag(name, value));
		}

		/**
		 * Adds a <code>TAG_String</code> entry.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putString(final String name, final String value) {

			return put(new StringTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Byte_Array</code> entry holding a copy of the array.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putByteArray(final String name, final byte[] value) {

			return put(new ByteArrayTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Int_Array</code> entry holding a copy of the array.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putIntArray(final String name, final int[] value) {

			return put(new IntArrayTag(name, value));
		}

		/**
		 * Adds a <code>TAG_Long_Array</code> entry holding a copy of the array.
		 *
		 * @param name
		 *            The entry name.
		 * @param value
		 *            The value.
		 * @return This builder.
		 */
		public Builder putLongArray(final String name, final long[] value) {

			return put(new LongArrayTag(name, value));
		}

		/**
		 * Adds a nested <code>TAG_Compound</code> entry, filled in by the
		 * given function, such as
		 * <code>putCompound("Pos", pos -&gt; pos.putInt("x", 1))</code>.
		 *
		 * @param name
		 *            The entry name.
		 * @param contents
		 *            Adds the nested compound's entries to its builder.
		 * @return This builder.
		 */
		public Builder putCompound(final String name, final Consumer<Builder> contents) {

			final Builder nested = new Builder(name);
			contents.accept(nested);
			return put(nested.build());
		}

		/**
		 * Removes an entry, if it exists.
		 *
		 * @param name
		 *            The entry name.
		 * @return This builder.
		 */
		public Builder remove(final String name) {

			entries.remove(name);
			return this;
		}

		/**
		 * Builds the compound.
		 *
		 * @return The compound.
		 */
		public CompoundTag build() {

			return new CompoundTag(name, entries);
		}
	}

	@Override
	public Map<String, Tag> getValue() {

		return value;
	}

	/**
	 * Checks whether this compound has an entry with the given name.
	 *
	 * @param name
	 *            The entry name.
	 * @return <code>true</code> if the entry exists.
	 */
	public boolean contains(final String name) {

		return value.containsKey(name);
	}

	/**
	 * Checks whether this compound has an entry with the given name and type.
	 *
	 * @param name
	 *            The entry name.
	 * @param type
	 *            The tag class, such as <code>IntTag.class</code>.
	 * @return <code>true</code> if the entry exists and has that type.
	 */
	public boolean contains(final String name, final Class<? extends Tag> type) {

		return type.isInstance(value.get(name));
	}

	/**
	 * Gets an entry of the given type, if it exists.
	 *
	 * @param <T>
	 *            The tag type.
	 * @param name
	 *            The entry name.
	 * @param type
	 *            The tag class, such as <code>CompoundTag.class</code>.
	 * @return The entry, or an empty <code>Optional</code> if there is no
	 *         entry with that name.
	 * @throws ClassCastException
	 *             if the entry exists but has a different type.
	 */
	public <T extends Tag> Optional<T> find(final String name, final Class<T> type) {

		final Tag tag = value.get(name);
		if (tag == null) {
			return Optional.empty();
		}
		if (!type.isInstance(tag)) {
			throw new ClassCastException("[JNBT] " + describeEntry(name) + " is "
					+ NBTUtils.getTypeName(tag.getClass()) + ", not "
					+ NBTUtils.getTypeName(type) + ".");
		}
		return Optional.of(type.cast(tag));
	}

	/**
	 * Gets an entry of the given type.
	 *
	 * @param <T>
	 *            The tag type.
	 * @param name
	 *            The entry name.
	 * @param type
	 *            The tag class, such as <code>CompoundTag.class</code>.
	 * @return The entry.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public <T extends Tag> T get(final String name, final Class<T> type) {

		return find(name, type).orElseThrow(() -> new NoSuchElementException(
				"[JNBT] " + describeEntry(name) + " does not exist."));
	}

	/**
	 * Gets the value of a <code>TAG_Byte</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public byte getByte(final String name) {

		return get(name, ByteTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_Short</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public short getShort(final String name) {

		return get(name, ShortTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_Int</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public int getInt(final String name) {

		return get(name, IntTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_Long</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public long getLong(final String name) {

		return get(name, LongTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_Float</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public float getFloat(final String name) {

		return get(name, FloatTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_Double</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public double getDouble(final String name) {

		return get(name, DoubleTag.class).getValue();
	}

	/**
	 * Gets the value of a <code>TAG_String</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public String getString(final String name) {

		return get(name, StringTag.class).getValue();
	}

	/**
	 * Gets a copy of the value of a <code>TAG_Byte_Array</code> entry. To read
	 * elements without copying, use <code>get(name, ByteArrayTag.class)</code>.
	 *
	 * @param name
	 *            The entry name.
	 * @return A copy of the value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public byte[] getByteArray(final String name) {

		return get(name, ByteArrayTag.class).getValue();
	}

	/**
	 * Gets a copy of the value of a <code>TAG_Int_Array</code> entry. To read
	 * elements without copying, use <code>get(name, IntArrayTag.class)</code>.
	 *
	 * @param name
	 *            The entry name.
	 * @return A copy of the value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public int[] getIntArray(final String name) {

		return get(name, IntArrayTag.class).getValue();
	}

	/**
	 * Gets a copy of the value of a <code>TAG_Long_Array</code> entry. To read
	 * elements without copying, use <code>get(name, LongArrayTag.class)</code>.
	 *
	 * @param name
	 *            The entry name.
	 * @return A copy of the value.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public long[] getLongArray(final String name) {

		return get(name, LongArrayTag.class).getValue();
	}

	/**
	 * Gets a <code>TAG_Compound</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The compound.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public CompoundTag getCompound(final String name) {

		return get(name, CompoundTag.class);
	}

	/**
	 * Gets a <code>TAG_List</code> entry.
	 *
	 * @param name
	 *            The entry name.
	 * @return The list.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry has a different type.
	 */
	public ListTag getList(final String name) {

		return get(name, ListTag.class);
	}

	/**
	 * Gets the elements of a <code>TAG_List</code> entry whose elements have
	 * the given type, such as <code>getList("sections", CompoundTag.class)</code>.
	 * An empty list matches any element type, since Minecraft often writes
	 * empty lists with the element type <code>TAG_End</code>.
	 *
	 * @param <T>
	 *            The element type.
	 * @param name
	 *            The entry name.
	 * @param elementType
	 *            The element tag class.
	 * @return The elements, as a read-only list.
	 * @throws NoSuchElementException
	 *             if there is no entry with that name.
	 * @throws ClassCastException
	 *             if the entry is not a list, or is a non-empty list of a
	 *             different element type.
	 */
	public <T extends Tag> List<T> getList(final String name, final Class<T> elementType) {

		final ListTag list = getList(name);
		if (list.getValue().isEmpty()) {
			return Collections.emptyList();
		}
		if (list.getType() != elementType) {
			throw new ClassCastException("[JNBT] " + describeEntry(name) + " is a list of "
					+ NBTUtils.getTypeName(list.getType()) + ", not "
					+ NBTUtils.getTypeName(elementType) + ".");
		}
		@SuppressWarnings("unchecked") // The ListTag constructor checks every element's type.
		final List<T> elements = (List<T>) (List<?>) list.getValue();
		return elements;
	}

	/**
	 * Names an entry of this compound for error messages, such as
	 * <code>"x" in TAG_Compound("Level")</code>.
	 */
	private String describeEntry(final String name) {

		return "\"" + name + "\" in " + describe();
	}

	@Override
	public String toString() {
	
		final StringBuilder bldr = new StringBuilder();
		bldr.append(describe()).append(": ").append(value.size()).append(" entries\n{\n");
		for (final Tag tag : value.values()) {
			bldr.append("   ").append(tag.toString().replace("\n", "\n   ")).append('\n');
		}
		bldr.append("}");
		return bldr.toString();
	}
	
	@Override
	public int hashCode() {
	
		final int prime = 31;
		int result = super.hashCode();
		result = (prime * result) + ((value == null) ? 0 : value.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(final Object obj) {
	
		if (this == obj) { return true; }
		if (!super.equals(obj)) { return false; }
		if (!(obj instanceof CompoundTag)) { return false; }
		final CompoundTag other = (CompoundTag) obj;
		if (value == null) {
			if (other.value != null) { return false; }
		} else if (!value.equals(other.value)) { return false; }
		return true;
	}
	
}
