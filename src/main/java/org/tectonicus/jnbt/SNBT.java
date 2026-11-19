package org.tectonicus.jnbt;

import java.util.List;
import java.util.Map;

/**
 * Writes tags as SNBT (stringified NBT), the text form Minecraft Java Edition
 * uses in commands and data packs, such as
 * <code>{Count:1b,id:"minecraft:stone",Pos:[1.5d,64.0d,0.0d]}</code>.
 * <p>
 * The output follows the syntax that every Minecraft version since 1.13
 * reads:
 * </p>
 * <ul>
 * <li>Numbers carry a type suffix, except ints: <code>1b</code>,
 * <code>2s</code>, <code>3</code>, <code>4L</code>, <code>5.0f</code>,
 * <code>6.0d</code>.</li>
 * <li>Arrays are written as <code>[B;1b,2b]</code>, <code>[I;1,2]</code> and
 * <code>[L;1L,2L]</code>.</li>
 * <li>Strings are in double quotes, with only <code>\</code> and
 * <code>"</code> escaped by a backslash; other characters, including line
 * breaks, are written as they are.</li>
 * <li>Compound keys are left unquoted when they contain only letters, digits
 * and <code>_ - . +</code>, and quoted otherwise.</li>
 * </ul>
 * <p>
 * SNBT has no place for the name of the outermost tag, so it is left out.
 * Float and double values that are NaN or infinite are written as Java prints
 * them, as Minecraft does, but Minecraft reads them back as strings.
 * </p>
 */
public final class SNBT {

	/**
	 * The indent used for each nesting level by {@link #toPrettyString(Tag)}.
	 */
	private static final String INDENT = "    ";

	/**
	 * Default private constructor.
	 */
	private SNBT() {

	}

	/**
	 * Writes a tag as compact SNBT on one line, as Minecraft prints it.
	 *
	 * @param tag
	 *            The tag.
	 * @return The SNBT text.
	 * @throws IllegalArgumentException
	 *             if the tag is an <code>EndTag</code>, which SNBT cannot
	 *             represent.
	 */
	public static String toString(final Tag tag) {

		final StringBuilder out = new StringBuilder();
		write(tag, out, null, 0);
		return out.toString();
	}

	/**
	 * Writes a tag as indented SNBT across several lines, for reading by
	 * people. Each compound entry, and each element of a list of compounds or
	 * lists, goes on its own line; other lists and arrays stay on one line.
	 * The result reads back the same as {@link #toString(Tag)}.
	 *
	 * @param tag
	 *            The tag.
	 * @return The SNBT text, with lines separated by <code>\n</code>.
	 * @throws IllegalArgumentException
	 *             if the tag is an <code>EndTag</code>, which SNBT cannot
	 *             represent.
	 */
	public static String toPrettyString(final Tag tag) {

		final StringBuilder out = new StringBuilder();
		write(tag, out, INDENT, 0);
		return out.toString();
	}

	/**
	 * Writes one tag's value.
	 *
	 * @param indent
	 *            The indent per level, or <code>null</code> for compact
	 *            output.
	 * @param depth
	 *            The nesting level of the tag.
	 */
	private static void write(final Tag tag, final StringBuilder out, final String indent,
			final int depth) {

		switch (tag) {
			case ByteTag t -> out.append(t.getValue()).append('b');
			case ShortTag t -> out.append(t.getValue()).append('s');
			case IntTag t -> out.append(t.getValue());
			case LongTag t -> out.append(t.getValue()).append('L');
			case FloatTag t -> out.append(t.getValue()).append('f');
			case DoubleTag t -> out.append(t.getValue()).append('d');
			case StringTag t -> quote(t.getValue(), out);
			case ByteArrayTag t -> {
				out.append("[B;");
				for (int i = 0; i < t.length(); i++) {
					separate(i, out, indent);
					out.append(t.get(i)).append('b');
				}
				out.append(']');
			}
			case IntArrayTag t -> {
				out.append("[I;");
				for (int i = 0; i < t.length(); i++) {
					separate(i, out, indent);
					out.append(t.get(i));
				}
				out.append(']');
			}
			case LongArrayTag t -> {
				out.append("[L;");
				for (int i = 0; i < t.length(); i++) {
					separate(i, out, indent);
					out.append(t.get(i)).append('L');
				}
				out.append(']');
			}
			case ListTag t -> writeList(t, out, indent, depth);
			case CompoundTag t -> writeCompound(t, out, indent, depth);
			default -> throw new IllegalArgumentException("[JNBT] "
					+ NBTUtils.getTypeName(tag.getClass()) + " cannot be written as SNBT.");
		}
	}

	private static void writeList(final ListTag list, final StringBuilder out, final String indent,
			final int depth) {

		final List<Tag> elements = list.getValue();
		final boolean multiline = indent != null && !elements.isEmpty()
				&& (list.getType() == CompoundTag.class || list.getType() == ListTag.class);
		out.append('[');
		for (int i = 0; i < elements.size(); i++) {
			if (multiline) {
				out.append(i == 0 ? "" : ",");
				newline(out, indent, depth + 1);
			} else {
				separate(i, out, indent);
			}
			write(elements.get(i), out, indent, depth + 1);
		}
		if (multiline) {
			newline(out, indent, depth);
		}
		out.append(']');
	}

	private static void writeCompound(final CompoundTag compound, final StringBuilder out,
			final String indent, final int depth) {

		final Map<String, Tag> entries = compound.getValue();
		out.append('{');
		int i = 0;
		for (final Map.Entry<String, Tag> entry : entries.entrySet()) {
			out.append(i == 0 ? "" : ",");
			if (indent != null) {
				newline(out, indent, depth + 1);
			}
			writeKey(entry.getKey(), out);
			out.append(indent == null ? ":" : ": ");
			write(entry.getValue(), out, indent, depth + 1);
			i++;
		}
		if (indent != null && !entries.isEmpty()) {
			newline(out, indent, depth);
		}
		out.append('}');
	}

	/**
	 * Writes the comma before an element on the same line, followed by a space
	 * in pretty output.
	 */
	private static void separate(final int index, final StringBuilder out, final String indent) {

		if (index > 0) {
			out.append(indent == null ? "," : ", ");
		}
	}

	private static void newline(final StringBuilder out, final String indent, final int depth) {

		out.append('\n');
		for (int i = 0; i < depth; i++) {
			out.append(indent);
		}
	}

	/**
	 * Writes a compound key, quoted only if it contains characters other than
	 * letters, digits and <code>_ - . +</code>, or is empty.
	 */
	private static void writeKey(final String key, final StringBuilder out) {

		boolean simple = !key.isEmpty();
		for (int i = 0; simple && i < key.length(); i++) {
			final char c = key.charAt(i);
			simple = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
					|| c == '_' || c == '-' || c == '.' || c == '+';
		}
		if (simple) {
			out.append(key);
		} else {
			quote(key, out);
		}
	}

	/**
	 * Writes a string in double quotes, escaping backslashes and double
	 * quotes.
	 */
	private static void quote(final String value, final StringBuilder out) {

		out.append('"');
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\\' || c == '"') {
				out.append('\\');
			}
			out.append(c);
		}
		out.append('"');
	}
}
