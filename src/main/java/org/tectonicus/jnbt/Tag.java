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

import java.util.Objects;

/**
 * Represents a single NBT tag.
 * 
 * @author Graham Edgecombe
 * 
 */
public abstract class Tag {
	
	/**
	 * The name of this tag.
	 */
	private final String name;
	
	/**
	 * Creates the tag with the specified name.
	 * 
	 * @param name
	 *            The name; use an empty string for an unnamed tag.
	 * @throws NullPointerException
	 *             if the name is null.
	 */
	public Tag(final String name) {
	
		this.name = Objects.requireNonNull(name, "name");
	}
	
	/**
	 * Gets the name of this tag.
	 * 
	 * @return The name of this tag.
	 */
	public final String getName() {
	
		return name;
	}
	
	/**
	 * Gets the value of this tag.
	 * 
	 * @return The value of this tag.
	 */
	public abstract Object getValue();

	/**
	 * Formats the start of <code>toString()</code>: the tag type, followed by
	 * the name in quotes if the tag has one, such as <code>TAG_Int("x")</code>.
	 *
	 * @return The tag type and name.
	 */
	final String describe() {

		final String typeName = NBTUtils.getTypeName(getClass());
		return name.isEmpty() ? typeName : typeName + "(\"" + name + "\")";
	}

	@Override
	public int hashCode() {

		return 31 + name.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {

		if (this == obj) { return true; }
		if (!(obj instanceof Tag)) { return false; }
		return name.equals(((Tag) obj).name);
	}
	
}
