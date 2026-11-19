package org.tectonicus.jnbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A Bedrock Edition <code>level.dat</code> file: an 8-byte header followed by
 * an uncompressed compound tag in the {@linkplain NBTFormat#BEDROCK Bedrock
 * format}.
 * <p>
 * The header holds two little-endian 32-bit integers: the storage version,
 * and the length in bytes of the NBT that follows. <code>level.dat_old</code>
 * has the same layout.
 * </p>
 *
 * @param storageVersion
 *            The storage version from the header, which Bedrock also stores
 *            as <code>StorageVersion</code> inside the data.
 * @param data
 *            The root compound tag.
 */
public record BedrockLevelDat(int storageVersion, CompoundTag data) {

	/**
	 * Creates a level.dat value.
	 *
	 * @param storageVersion
	 *            The storage version for the header.
	 * @param data
	 *            The root compound tag.
	 * @throws NullPointerException
	 *             if the data is null.
	 */
	public BedrockLevelDat {
		Objects.requireNonNull(data, "data");
	}

	/**
	 * Reads a Bedrock <code>level.dat</code> file.
	 *
	 * @param file
	 *            The file.
	 * @return The header and data.
	 * @throws IOException
	 *             if an I/O error occurs or the file is not a valid Bedrock
	 *             <code>level.dat</code>.
	 */
	public static BedrockLevelDat read(final Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return read(in);
		}
	}

	/**
	 * Reads a Bedrock <code>level.dat</code> from a stream, with the
	 * {@linkplain NBTReadLimits#DEFAULT default read limits}. The stream is
	 * left open.
	 *
	 * @param in
	 *            The stream, positioned at the start of the header.
	 * @return The header and data.
	 * @throws IOException
	 *             if an I/O error occurs or the data is not a valid Bedrock
	 *             <code>level.dat</code>.
	 */
	public static BedrockLevelDat read(final InputStream in) throws IOException {
		return read(in, NBTReadLimits.DEFAULT);
	}

	/**
	 * Reads a Bedrock <code>level.dat</code> from a stream. The stream is left
	 * open.
	 *
	 * @param in
	 *            The stream, positioned at the start of the header.
	 * @param limits
	 *            The resource limits to enforce while reading.
	 * @return The header and data.
	 * @throws IOException
	 *             if an I/O error occurs, the stream ends early, the length in
	 *             the header does not match the NBT, or the root tag is not a
	 *             compound.
	 */
	public static BedrockLevelDat read(final InputStream in, final NBTReadLimits limits)
			throws IOException {
		final DataInputStream header = new DataInputStream(in);
		final int storageVersion = Integer.reverseBytes(header.readInt());
		final int length = Integer.reverseBytes(header.readInt());
		if (length < 0) {
			throw new IOException("[JNBT] Invalid Bedrock level.dat length: " + length + ".");
		}
		// readNBytes grows its buffer as data arrives, so a corrupt length cannot
		// allocate more memory than the stream actually holds.
		final byte[] nbt = in.readNBytes(length);
		if (nbt.length != length) {
			throw new EOFException("[JNBT] Bedrock level.dat header gives " + length
					+ " bytes of NBT, but only " + nbt.length + " follow.");
		}
		final ByteArrayInputStream body = new ByteArrayInputStream(nbt);
		final Tag root;
		try (NBTInputStream reader = new NBTInputStream(body, NBTCompression.NONE, limits,
				NBTFormat.BEDROCK)) {
			root = reader.readTag();
		}
		if (body.available() != 0) {
			throw new IOException("[JNBT] Bedrock level.dat has " + body.available()
					+ " bytes left over after its root tag.");
		}
		if (!(root instanceof CompoundTag compound)) {
			throw new IOException("[JNBT] Bedrock level.dat root is "
					+ NBTUtils.getTypeName(root.getClass()) + ", not TAG_Compound.");
		}
		return new BedrockLevelDat(storageVersion, compound);
	}

	/**
	 * Writes this as a Bedrock <code>level.dat</code> file, replacing any
	 * existing file.
	 *
	 * @param file
	 *            The file.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void write(final Path file) throws IOException {
		try (OutputStream out = Files.newOutputStream(file)) {
			write(out);
		}
	}

	/**
	 * Writes this as a Bedrock <code>level.dat</code>: the header, then the
	 * data. The stream is flushed but left open.
	 *
	 * @param out
	 *            The stream.
	 * @throws IOException
	 *             if an I/O error occurs.
	 */
	public void write(final OutputStream out) throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		try (NBTOutputStream writer = new NBTOutputStream(body, NBTCompression.NONE,
				NBTFormat.BEDROCK)) {
			writer.writeTag(data);
		}
		final DataOutputStream header = new DataOutputStream(out);
		header.writeInt(Integer.reverseBytes(storageVersion));
		header.writeInt(Integer.reverseBytes(body.size()));
		body.writeTo(out);
		out.flush();
	}
}
