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

import java.util.Arrays;
import java.util.Objects;

/**
 * The <code>TAG_Int_Array</code> tag.
 * 
 * @author Graham Edgecombe
 * 
 */
public final class IntArrayTag extends Tag {
	
	/**
	 * The value.
	 */
	private final int[] value;
	
	/**
	 * Creates the tag.
	 * 
	 * @param name
	 *            The name.
	 * @param value
	 *            The value, which is copied.
	 */
	public IntArrayTag(final String name, final int[] value) {
	
		super(name);
		this.value = Arrays.copyOf(Objects.requireNonNull(value, "value"), value.length);
	}
	
	/**
	 * Gets a copy of the value. To read single elements without copying the
	 * whole array, use {@link #length()} and {@link #get(int)}.
	 * 
	 * @return A copy of the value.
	 */
	@Override
	public int[] getValue() {
	
		return Arrays.copyOf(value, value.length);
	}
	
	/**
	 * Gets the number of elements in the array.
	 * 
	 * @return The array length.
	 */
	public int length() {
	
		return value.length;
	}
	
	/**
	 * Gets one element of the array without copying it.
	 * 
	 * @param index
	 *            The element index.
	 * @return The element.
	 * @throws ArrayIndexOutOfBoundsException
	 *             if the index is out of range.
	 */
	public int get(final int index) {
	
		return value[index];
	}
	
	@Override
	public String toString() {
	
		return describe() + ": " + NBTUtils.joinElements(value.length, i -> Integer.toString(value[i]));
	}
	
	@Override
	public int hashCode() {
	
		return (31 * super.hashCode()) + Arrays.hashCode(value);
	}
	
	@Override
	public boolean equals(final Object obj) {
	
		if (this == obj) { return true; }
		if (!super.equals(obj)) { return false; }
		if (!(obj instanceof IntArrayTag)) { return false; }
		return Arrays.equals(value, ((IntArrayTag) obj).value);
	}
	
}
