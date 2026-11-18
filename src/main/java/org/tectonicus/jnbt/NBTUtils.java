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

import java.util.List;
import java.util.function.IntFunction;

/**
 * A class which contains NBT-related utility methods.
 * 
 * @author Graham Edgecombe
 * 
 */
public final class NBTUtils {
	
	/**
	 * The tag classes, indexed by their type code.
	 */
	private static final List<Class<? extends Tag>> TYPE_CLASSES = List.of(
			EndTag.class, ByteTag.class, ShortTag.class, IntTag.class,
			LongTag.class, FloatTag.class, DoubleTag.class, ByteArrayTag.class,
			StringTag.class, ListTag.class, CompoundTag.class, IntArrayTag.class,
			LongArrayTag.class);
	
	/**
	 * The tag type names, indexed by their type code.
	 */
	private static final List<String> TYPE_NAMES = List.of(
			"TAG_End", "TAG_Byte", "TAG_Short", "TAG_Int",
			"TAG_Long", "TAG_Float", "TAG_Double", "TAG_Byte_Array",
			"TAG_String", "TAG_List", "TAG_Compound", "TAG_Int_Array",
			"TAG_Long_Array");
	
	/**
	 * The most array elements that a tag's <code>toString()</code> shows.
	 */
	static final int TO_STRING_MAX_ELEMENTS = 64;
	
	/**
	 * Gets the type name of a tag.
	 * 
	 * @param clazz
	 *            The tag class.
	 * @return The type name.
	 * @throws IllegalArgumentException
	 *             if the tag class is invalid.
	 */
	public static String getTypeName(final Class<? extends Tag> clazz) {
	
		return TYPE_NAMES.get(getTypeCode(clazz));
	}
	
	/**
	 * Gets the type code of a tag class.
	 * 
	 * @param clazz
	 *            The tag class.
	 * @return The type code.
	 * @throws IllegalArgumentException
	 *             if the tag class is invalid.
	 */
	public static int getTypeCode(final Class<? extends Tag> clazz) {
	
		final int type = TYPE_CLASSES.indexOf(clazz);
		if (type < 0) {
			throw new IllegalArgumentException("[JNBT] Invalid tag class ("
					+ (clazz == null ? "null" : clazz.getName()) + ").");
		}
		return type;
	}
	
	/**
	 * Gets the class of a type of tag.
	 * 
	 * @param type
	 *            The type.
	 * @return The class.
	 * @throws IllegalArgumentException
	 *             if the tag type is invalid.
	 */
	public static Class<? extends Tag> getTypeClass(final int type) {
	
		if (type < 0 || type >= TYPE_CLASSES.size()) {
			throw new IllegalArgumentException("[JNBT] Invalid tag type : " + type + ".");
		}
		return TYPE_CLASSES.get(type);
	}
	
	/**
	 * Joins array elements with spaces for a tag's <code>toString()</code>,
	 * showing at most {@link #TO_STRING_MAX_ELEMENTS} of them.
	 * 
	 * @param length
	 *            The number of elements.
	 * @param element
	 *            Formats the element at an index.
	 * @return The joined elements, with a count of any left out.
	 */
	static String joinElements(final int length, final IntFunction<String> element) {
	
		final StringBuilder bldr = new StringBuilder();
		final int shown = Math.min(length, TO_STRING_MAX_ELEMENTS);
		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				bldr.append(' ');
			}
			bldr.append(element.apply(i));
		}
		if (length > shown) {
			bldr.append(" ... (").append(length - shown).append(" more)");
		}
		return bldr.toString();
	}
	
	/**
	 * Default private constructor.
	 */
	private NBTUtils() {
	
	}
}
