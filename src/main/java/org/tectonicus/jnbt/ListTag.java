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
import java.util.ArrayList;

/**
 * The <code>TAG_List</code> tag.
 * 
 * @author Graham Edgecombe
 * 
 */
public final class ListTag extends Tag {
	
	/**
	 * The type.
	 */
	private final Class<? extends Tag> type;
	
	/**
	 * The value.
	 */
	private final List<Tag> value;
	
	/**
	 * Creates the tag.
	 * 
	 * @param name
	 *            The name.
	 * @param type
	 *            The type of item in the list.
	 * @param value
	 *            The value. NBT list elements are unnamed, so any element
	 *            with a name is stored as an unnamed copy, matching what is
	 *            read back from a stream.
	 */
	public ListTag(final String name, final Class<? extends Tag> type, final List<Tag> value) {
	
		super(name);
		if (type == null || value == null) {
			throw new NullPointerException("List type and value are required.");
		}
		try {
			NBTUtils.getTypeCode(type);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported NBT list element type: "
					+ type.getName() + ".", e);
		}
		if (type == EndTag.class && !value.isEmpty()) {
			throw new IllegalArgumentException("TAG_End is only valid as the type of an empty list.");
		}
		final List<Tag> copy = new ArrayList<Tag>(value.size());
		for (final Tag tag : value) {
			if (tag == null || tag.getClass() != type) {
				throw new IllegalArgumentException("List elements must all have type " + type.getName() + ".");
			}
			// The NBT format stores no names for list elements.
			copy.add(tag.withName(""));
		}
		this.type = type;
		this.value = Collections.unmodifiableList(copy);
	}

	/**
	 * Creates a builder for a list with the given name and element type.
	 *
	 * @param <T>
	 *            The element type.
	 * @param name
	 *            The list's name; use an empty string for an unnamed list.
	 * @param type
	 *            The element tag class, such as <code>CompoundTag.class</code>.
	 * @return The builder.
	 */
	public static <T extends Tag> Builder<T> builder(final String name, final Class<T> type) {

		return new Builder<T>(name, type);
	}

	/**
	 * Creates a builder for an unnamed list with the given element type, as
	 * used for a list inside another list, or one to pass to
	 * {@link CompoundTag.Builder#put(String, Tag)}.
	 *
	 * @param <T>
	 *            The element type.
	 * @param type
	 *            The element tag class, such as <code>CompoundTag.class</code>.
	 * @return The builder.
	 */
	public static <T extends Tag> Builder<T> builder(final Class<T> type) {

		return builder("", type);
	}

	/**
	 * Builds a {@link ListTag} one element at a time. Element names are
	 * dropped, since NBT list elements are unnamed.
	 *
	 * @param <T>
	 *            The element type.
	 */
	public static final class Builder<T extends Tag> {
		private final String name;
		private final Class<T> type;
		private final List<Tag> elements = new ArrayList<Tag>();

		private Builder(final String name, final Class<T> type) {
			if (name == null || type == null) {
				throw new NullPointerException("List name and type are required.");
			}
			this.name = name;
			this.type = type;
		}

		/**
		 * Adds an element.
		 *
		 * @param element
		 *            The element.
		 * @return This builder.
		 * @throws NullPointerException
		 *             if the element is null.
		 */
		public Builder<T> add(final T element) {

			if (element == null) {
				throw new NullPointerException("List elements cannot be null.");
			}
			elements.add(element);
			return this;
		}

		/**
		 * Adds elements, in order.
		 *
		 * @param elements
		 *            The elements.
		 * @return This builder.
		 * @throws NullPointerException
		 *             if any element is null.
		 */
		public Builder<T> addAll(final Iterable<? extends T> elements) {

			for (final T element : elements) {
				add(element);
			}
			return this;
		}

		/**
		 * Builds the list.
		 *
		 * @return The list.
		 * @throws IllegalArgumentException
		 *             if the element type is not a supported tag type, or is
		 *             <code>EndTag</code> and elements were added.
		 */
		public ListTag build() {

			return new ListTag(name, type, elements);
		}
	}
	
	/**
	 * Gets the type of item in this list.
	 * 
	 * @return The type of item in this list.
	 */
	public Class<? extends Tag> getType() {
	
		return type;
	}
	
	@Override
	public List<Tag> getValue() {
	
		return value;
	}
	
	@Override
	public String toString() {
	
		final StringBuilder bldr = new StringBuilder();
		bldr.append(describe()).append(": ").append(value.size())
				.append(" entries of type ").append(NBTUtils.getTypeName(type)).append("\n{\n");
		for (final Tag t : value) {
			bldr.append("   ").append(t.toString().replace("\n", "\n   ")).append('\n');
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
		if (!(obj instanceof ListTag)) { return false; }
		final ListTag other = (ListTag) obj;
		if (value == null) {
			if (other.value != null) { return false; }
		} else if (!value.equals(other.value)) { return false; }
		return true;
	}
	
}
