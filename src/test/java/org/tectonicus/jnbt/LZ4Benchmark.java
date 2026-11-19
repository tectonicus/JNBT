package org.tectonicus.jnbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Compares JNBT's LZ4 streams with lz4-java on chunk-sized NBT, the way
 * Minecraft uses them: one stream per chunk.
 * <p>
 * Run with <code>./gradlew lz4Benchmark</code>, optionally with
 * <code>--args="path"</code> pointing at a world or saves folder (or a
 * <code>.mca</code> file) to benchmark real chunks. Without a path it uses
 * synthetic chunks.
 * </p>
 */
public final class LZ4Benchmark {
	private static final int MAX_CHUNKS = 2_000;
	private static final long ROUND_NANOS = 300_000_000L;
	private static final int WARMUP_ROUNDS = 8;
	private static final int ROUNDS = 7;
	private static volatile long sink;

	private LZ4Benchmark() {
	}

	interface Codec {
		OutputStream compressor(OutputStream out) throws IOException;

		InputStream decompressor(InputStream in) throws IOException;
	}

	public static void main(final String[] args) throws Exception {
		final List<byte[]> chunks = args.length > 0 ? loadChunks(Paths.get(args[0])) : syntheticChunks();
		long total = 0;
		for (final byte[] chunk : chunks) {
			total += chunk.length;
		}
		System.out.printf("Corpus: %d chunks, %.1f MB uncompressed, %.1f KB average%n", chunks.size(),
				total / 1e6, total / 1024.0 / chunks.size());
		System.out.printf("Java %s, %s%n%n", System.getProperty("java.version"), System.getProperty("os.arch"));

		final Map<String, Codec> codecs = new LinkedHashMap<String, Codec>();
		codecs.put("JNBT", new Codec() {
			public OutputStream compressor(final OutputStream out) { return new LZ4BlockOutputStream(out); }
			public InputStream decompressor(final InputStream in) { return new LZ4BlockInputStream(in); }
		});
		codecs.put("lz4-java native", lz4Java(LZ4Factory.fastestInstance(), XXHashFactory.fastestInstance()));
		codecs.put("lz4-java unsafe Java",
				lz4Java(LZ4Factory.unsafeInstance(), XXHashFactory.unsafeInstance()));
		codecs.put("lz4-java safe Java", lz4Java(LZ4Factory.safeInstance(), XXHashFactory.safeInstance()));

		System.out.printf("%-22s %14s %14s %8s%n", "Codec", "Compress MB/s", "Decomp. MB/s", "Ratio");
		for (final Map.Entry<String, Codec> entry : codecs.entrySet()) {
			final Codec codec = entry.getValue();
			final List<byte[]> compressed = new ArrayList<byte[]>();
			long compressedTotal = 0;
			for (final byte[] chunk : chunks) {
				final byte[] c = compress(codec, chunk);
				compressed.add(c);
				compressedTotal += c.length;
				if (!Arrays.equals(chunk, decompress(codec, c, new byte[chunk.length]))) {
					throw new AssertionError(entry.getKey() + " failed to round-trip");
				}
			}
			final double compressRate = measure(total, () -> {
				for (final byte[] chunk : chunks) {
					sink += compress(codec, chunk).length;
				}
			});
			final byte[] scratch = new byte[1 << 20];
			final double decompressRate = measure(total, () -> {
				for (final byte[] c : compressed) {
					sink += decompress(codec, c, scratch).length;
				}
			});
			System.out.printf("%-22s %14.0f %14.0f %8.2f%n", entry.getKey(), compressRate, decompressRate,
					(double) total / compressedTotal);
		}

		// For scale: parsing the decompressed NBT into tags.
		final double parseRate = measure(total, () -> {
			for (final byte[] chunk : chunks) {
				try (NBTInputStream in = new NBTInputStream(new ByteArrayInputStream(chunk), NBTCompression.NONE)) {
					sink += in.readTag().hashCode();
				}
			}
		});
		System.out.printf("%nNBT parsing alone: %.0f MB/s%n", parseRate);
	}

	private static Codec lz4Java(final LZ4Factory factory, final XXHashFactory hashes) {
		return new Codec() {
			public OutputStream compressor(final OutputStream out) {
				return new net.jpountz.lz4.LZ4BlockOutputStream(out, 1 << 16, factory.fastCompressor(),
						hashes.newStreamingHash32(LZ4Block.CHECKSUM_SEED).asChecksum(), false);
			}

			@SuppressWarnings("deprecation")
			public InputStream decompressor(final InputStream in) {
				return new net.jpountz.lz4.LZ4BlockInputStream(in, factory.fastDecompressor(),
						hashes.newStreamingHash32(LZ4Block.CHECKSUM_SEED).asChecksum());
			}
		};
	}

	private static byte[] compress(final Codec codec, final byte[] data) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length / 2 + 64);
		try (OutputStream out = codec.compressor(bytes)) {
			out.write(data);
		}
		return bytes.toByteArray();
	}

	private static byte[] decompress(final Codec codec, final byte[] data, final byte[] into) throws IOException {
		int length = 0;
		try (InputStream in = codec.decompressor(new ByteArrayInputStream(data))) {
			int count;
			while ((count = in.read(into, length, into.length - length)) > 0) {
				length += count;
			}
		}
		return Arrays.copyOf(into, length);
	}

	interface Work {
		void run() throws IOException;
	}

	/** Runs the work repeatedly and returns the median throughput in MB/s. */
	private static double measure(final long bytesPerRun, final Work work) throws IOException {
		final double[] rates = new double[ROUNDS];
		for (int round = -WARMUP_ROUNDS; round < ROUNDS; round++) {
			int runs = 0;
			final long start = System.nanoTime();
			long elapsed;
			do {
				work.run();
				runs++;
				elapsed = System.nanoTime() - start;
			} while (elapsed < ROUND_NANOS);
			if (round >= 0) {
				rates[round] = bytesPerRun * (double) runs / elapsed * 1e3;
			}
		}
		Arrays.sort(rates);
		return rates[ROUNDS / 2];
	}

	/** Reads the uncompressed NBT of up to MAX_CHUNKS chunks from region files under a path. */
	private static List<byte[]> loadChunks(final Path root) throws IOException {
		final List<Path> regions;
		try (Stream<Path> files = Files.walk(root)) {
			regions = files.filter(p -> p.toString().endsWith(".mca")
					&& p.getParent() != null && p.getParent().getFileName().toString().equals("region"))
					.sorted().toList();
		}
		final List<byte[]> chunks = new ArrayList<byte[]>();
		for (final Path region : regions) {
			try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "r")) {
				if (file.length() < 8192) {
					continue;
				}
				final int[] locations = new int[1024];
				for (int i = 0; i < 1024; i++) {
					locations[i] = file.readInt();
				}
				for (final int location : locations) {
					if (location == 0) {
						continue;
					}
					file.seek((long) (location >>> 8) * 4096);
					final int length = file.readInt();
					final int type = file.readUnsignedByte();
					if (length <= 1 || length > 1 << 20 || (type & 0x80) != 0) {
						continue;
					}
					final byte[] payload = new byte[length - 1];
					file.readFully(payload);
					final InputStream raw = new ByteArrayInputStream(payload);
					final InputStream in = switch (type) {
						case 1 -> new GZIPInputStream(raw);
						case 2 -> new InflaterInputStream(raw);
						case 3 -> raw;
						case 4 -> new LZ4BlockInputStream(raw);
						default -> null;
					};
					if (in != null) {
						chunks.add(new DataInputStream(in).readAllBytes());
						if (chunks.size() == MAX_CHUNKS) {
							return chunks;
						}
					}
				}
			}
		}
		if (chunks.isEmpty()) {
			throw new IOException("No chunks found under " + root);
		}
		return chunks;
	}

	/** Builds chunk-shaped NBT: sections with block palettes and packed long arrays. */
	private static List<byte[]> syntheticChunks() throws IOException {
		final Random random = new Random(1234);
		final String[] blocks = { "minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
				"minecraft:deepslate", "minecraft:air", "minecraft:water", "minecraft:coal_ore",
				"minecraft:iron_ore", "minecraft:gravel", "minecraft:andesite" };
		final List<byte[]> chunks = new ArrayList<byte[]>();
		for (int c = 0; c < 500; c++) {
			final List<Tag> sections = new ArrayList<Tag>();
			for (int y = -4; y < 20; y++) {
				final Map<String, Tag> section = new LinkedHashMap<String, Tag>();
				section.put("Y", new ByteTag("Y", (byte) y));
				final int paletteSize = y > 8 ? 1 : 2 + random.nextInt(8);
				final List<Tag> palette = new ArrayList<Tag>();
				for (int i = 0; i < paletteSize; i++) {
					palette.add(new CompoundTag("", Map.of("Name", new StringTag("Name", blocks[(y + 4 + i) % blocks.length]))));
				}
				final Map<String, Tag> states = new LinkedHashMap<String, Tag>();
				states.put("palette", new ListTag("palette", CompoundTag.class, palette));
				if (paletteSize > 1) {
					// Mostly the first palette entry, with runs of others, as terrain tends to be.
					final long[] data = new long[4096 * 4 / 64];
					for (int i = 0; i < data.length; i++) {
						long word = 0;
						for (int j = 0; j < 16; j++) {
							final long value = random.nextInt(10) < 7 ? 0 : random.nextInt(paletteSize);
							word |= value << (j * 4);
						}
						data[i] = word;
					}
					states.put("data", new LongArrayTag("data", data));
				}
				section.put("block_states", new CompoundTag("block_states", states));
				section.put("biomes", new CompoundTag("biomes", Map.of("palette", new ListTag("palette",
						StringTag.class, List.of(new StringTag("", "minecraft:plains"))))));
				sections.add(new CompoundTag("", section));
			}
			final Map<String, Tag> root = new LinkedHashMap<String, Tag>();
			root.put("DataVersion", new IntTag("DataVersion", 4325));
			root.put("xPos", new IntTag("xPos", c % 32));
			root.put("zPos", new IntTag("zPos", c / 32));
			root.put("Status", new StringTag("Status", "minecraft:full"));
			root.put("sections", new ListTag("sections", CompoundTag.class, sections));
			final long[] heights = new long[37];
			for (int i = 0; i < heights.length; i++) {
				heights[i] = random.nextLong() & 0x00FF00FF00FF00FFL;
			}
			root.put("Heightmaps", new CompoundTag("Heightmaps",
					Map.of("WORLD_SURFACE", new LongArrayTag("WORLD_SURFACE", heights))));
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (NBTOutputStream out = new NBTOutputStream(bytes, NBTCompression.NONE)) {
				out.writeTag(new CompoundTag("", root));
			}
			chunks.add(bytes.toByteArray());
		}
		return chunks;
	}
}
