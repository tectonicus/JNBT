# JNBT

JNBT is a Java library for reading and writing NBT (Named Binary Tag) files, the binary format Minecraft uses for
worlds, chunks, player data and structures. The format is described at
[minecraft.wiki/w/NBT_format](https://minecraft.wiki/w/NBT_format).

This is the [Tectonicus](https://github.com/tectonicus/tectonicus) fork of JNBT, originally written by Graham Edgecombe
([jnbt.org](http://jnbt.org/)). It requires Java 21 or later and has no dependencies.

## Installation

JNBT is published to Maven Central as `org.tectonicus:jnbt`.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("org.tectonicus:jnbt:2.0.0")
}
```

Maven:

```xml
<dependency>
    <groupId>org.tectonicus</groupId>
    <artifactId>jnbt</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Usage

Reading a file such as `level.dat`, detecting its compression:

```java
try (NBTInputStream in = new NBTInputStream(Files.newInputStream(path))) {
    CompoundTag root = (CompoundTag) in.readTag();
    CompoundTag data = root.getCompound("Data");
    System.out.println(data.getString("LevelName"));
}
```

`CompoundTag` has typed getters for every tag type, such as `getInt`, `getString`, `getCompound` and
`getList(name, CompoundTag.class)`. They throw `NoSuchElementException` for a missing entry and `ClassCastException`
for one of another type, with a message naming the entry. For optional entries, use `find(name, IntTag.class)`, which
returns an `Optional`, or `contains(name)`.

Writing a tag:

```java
Map<String, Tag> entries = new LinkedHashMap<>();
entries.put("name", new StringTag("name", "Example"));
entries.put("heights", new IntArrayTag("heights", new int[] { 64, 65, 66 }));
CompoundTag root = new CompoundTag("", entries);

try (NBTOutputStream out = new NBTOutputStream(Files.newOutputStream(path), NBTCompression.GZIP)) {
    out.writeTag(root);
}
```

Things to know:

- **Compression:** `NBTCompression.NONE`, `GZIP` (used by `level.dat` and player files), `DEFLATE` (zlib, used for
  chunks inside region files) and `LZ4` (used for region file chunks since Minecraft 1.20.5 when
  `region-file-compression=lz4`, compression type 4). The same enum serves both streams. `LZ4` reads and writes the
  block format of lz4-java's `LZ4BlockOutputStream`, as Minecraft does, without needing lz4-java.
- **Detecting compression:** `new NBTInputStream(input)` and `new NBTInputStream(input, limits)` look at the first bytes
  to tell the formats apart, and `getCompression()` reports the result, so a file can be written back the same way.
  Unrecognized input fails with an `IOException`. Pass an `NBTCompression` to skip detection, and note that a
  `DataInputStream` is always read uncompressed. `NBTCompression.detect(bytes)` does the same check on a byte array.
- **Read limits:** reading checks every length in the stream before allocating memory, so a corrupt or malicious file
  fails with an `IOException` instead of exhausting memory. Pass an `NBTReadLimits` to change the defaults.
- **Immutable tags:** compound and list values are read-only, and array tags copy their arrays in and out. Use
  `length()` and `get(int)` on the array tags to read elements without copying.
- **Strings:** tag names and string values use Java's modified UTF-8, the same encoding as Minecraft.
- **Order:** compound entries keep their order, so reading and rewriting a file reproduces it byte for byte.

## Building

```shell
./gradlew build
```

This compiles the library, runs the tests and checks the Javadoc.

## Version history

### Unreleased

- Added `NBTCompression.LZ4` for Minecraft's LZ4-compressed region file chunks. Block checksums are verified when
  reading.

### 2.0.0 (Tectonicus)

- (Charlie Birks) `TAG_Long_Array` and zlib (Deflate) compression support.
- Added the `NBTCompression` enum (`NONE`, `GZIP`, `DEFLATE`) to choose the compression when reading or writing.
- Published to Maven Central as `org.tectonicus:jnbt`, in the `org.tectonicus.jnbt` package, with a Gradle build.
- Added `NBTReadLimits` to reject oversized or deeply nested input.
- Made tags immutable, and rejected invalid lists and compounds and null names or values at construction.
- Read and write strings as modified UTF-8, as Minecraft does, so null characters and emoji survive a round trip.
- Kept compound entries in order when reading, and stored list entries without names, as the format does.
- Closed the underlying stream when a constructor fails.
- Added `length()` and `get(int)` to the array tags, and shortened `toString()` output for long arrays.
- Added typed getters to `CompoundTag`, such as `getInt`, `getCompound` and `getList(name, type)`, plus `find` and
  `contains`.
- `NBTInputStream` detects the compression when none is given, instead of assuming GZip, and reports it through
  `getCompression()`.

### 1.4 (Morlok8k)

- (pruby) Support for streams that are not GZip-compressed.
- (ensirius) Support for reading from a raw `DataInputStream`.

### 1.3 (Morlok8k)

- Changed encoding of .java files to UTF-8.
- Added "[JNBT]" to error output.
- Added `equals` and `hashCode` to tags.
- Cleaned up source and formatting.

### 1.2 (Morlok8k)

- `TAG_Int_Array` support.

### 1.1 (grahamedgecombe)

- Fix for writing `TAG_List`.
- `toString()` methods on each tag type.

### 1.0 (grahamedgecombe)

- Initial release.

## Credits

- Graham Edgecombe (grahamedgecombe): JNBT 1.0 and 1.1
- osici: the `TAG_List` bug fix
- Jocopa3: initial `TAG_Int_Array` support
- Morlok8k: maintaining the project
- Charlie Birks and the Tectonicus contributors: the Tectonicus fork

## License

JNBT is available under the BSD 3-Clause license. See [LICENSE](LICENSE).
