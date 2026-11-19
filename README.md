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

JNBT is a Java module named `org.tectonicus.jnbt`. If your project uses modules, add it to your `module-info.java`:

```java
requires org.tectonicus.jnbt;
```

On the class path it works like any other JAR.

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
`getList(name, CompoundTag.class)`. For optional entries, use `find(name, IntTag.class)`, which
returns an `Optional`, or `contains(name)`.

Writing a tag, built with `CompoundTag.builder()` and `ListTag.builder(...)`:

```java
CompoundTag root = CompoundTag.builder()
    .putString("name", "Example")
    .putIntArray("heights", new int[] { 64, 65, 66 })
    .putCompound("spawn", spawn -> spawn.putInt("x", 0).putInt("y", 64).putInt("z", 0))
    .put("tags", ListTag.builder(StringTag.class)
        .add(new StringTag("", "first"))
        .add(new StringTag("", "second"))
        .build())
    .build();

try (NBTOutputStream out = new NBTOutputStream(Files.newOutputStream(path), NBTCompression.GZIP)) {
    out.writeTag(root);
}
```

`put(name, tag)` names a tag made elsewhere, such as a list or compound from its own builder. Putting a name again
replaces that entry in place. Tags are immutable, so to change one, call `toBuilder()` on a compound, change it, and
build a new one.

Printing a tag as SNBT, the text form Minecraft uses in commands:

```java
SNBT.toString(root);       // {name:"Example",heights:[I;64,65,66],spawn:{x:0,y:64,z:0},...}
SNBT.toPrettyString(root); // the same, indented across several lines
```

The output uses the syntax every Minecraft version since 1.13 reads. SNBT has no place for the outermost tag's name, so
it is left out. JNBT writes SNBT but does not parse it.

Things to know:

- **Compression:** `NBTCompression.NONE`, `GZIP` (used by `level.dat` and player files), `DEFLATE` (zlib, used for
  chunks inside region files) and `LZ4` (used for region file chunks since Minecraft 1.20.5 when
  `region-file-compression=lz4`, compression type 4).
- **Detecting compression:** `new NBTInputStream(input)` and `new NBTInputStream(input, limits)` look at the first bytes
  to tell the formats apart, and `getCompression()` reports the result, so a file can be written back the same way. Pass an `NBTCompression` to skip detection, and note that a
  `DataInputStream` is always read uncompressed. `NBTCompression.detect(bytes)` does the same check on a byte array.
- **Read limits:** reading checks every length in the stream before allocating memory, so a corrupt or malicious file
  fails with an `IOException` instead of exhausting memory. Pass an `NBTReadLimits` to change the defaults.
- **Immutable tags:** compound and list values are read-only, and array tags copy their arrays in and out. Use
  `length()` and `get(int)` on the array tags to read elements without copying.
- **Strings:** tag names and string values use Java's modified UTF-8, the same encoding as Minecraft Java Edition.
- **Order:** compound entries keep their order, so reading and rewriting a file reproduces it byte for byte.

### Bedrock Edition

Bedrock files use the same tags, but store numbers little-endian and strings as standard UTF-8. Pass
`NBTFormat.BEDROCK` to either stream, for example to read a `.mcstructure` file:

```java
try (NBTInputStream in = new NBTInputStream(Files.newInputStream(path), NBTFormat.BEDROCK)) {
    CompoundTag structure = (CompoundTag) in.readTag();
}
```

A Bedrock `level.dat` starts with an 8-byte header (the storage version and the length of the NBT), which
`BedrockLevelDat` reads and writes:

```java
BedrockLevelDat levelDat = BedrockLevelDat.read(worldFolder.resolve("level.dat"));
String name = levelDat.data().getString("LevelName");
```

The rest of a Bedrock world, such as chunks and entities, is stored in a LevelDB database, which JNBT does not read.
JNBT can decode NBT values that another library reads from it; some values hold several tags in a row, so call
`readTag()` until the data runs out.

## Building

```shell
./gradlew build
```

This compiles the library, runs the tests and checks the Javadoc. To also test against a real Bedrock world, set the
`JNBT_BEDROCK_LEVEL_DAT` environment variable to the path of its `level.dat`; the test reads it and checks that
writing it back reproduces the file exactly.

## Version history

### 2.0.0 (Tectonicus)

- (Charlie Birks) `TAG_Long_Array` and zlib (Deflate) compression support.
- Added the `NBTCompression` enum (`NONE`, `GZIP`, `DEFLATE`, `LZ4`) to choose the compression when reading or writing.
- Added `NBTCompression.LZ4` for Minecraft's LZ4-compressed region file chunks. Block checksums are verified when
  reading.
- Published to Maven Central as `org.tectonicus:jnbt`, in the `org.tectonicus.jnbt` package, with a Gradle build.
- Added `NBTReadLimits` to reject oversized or deeply nested input.
- Made tags immutable, and rejected invalid lists and compounds and null names or values at construction.
- Read and write strings as modified UTF-8, as Minecraft does, so null characters and emoji survive a round trip.
- Kept compound entries in order when reading, and stored list entries without names, as the format does.
- Closed the underlying stream when a constructor fails.
- Added `length()` and `get(int)` to the array tags, and shortened `toString()` output for long arrays.
- Added typed getters to `CompoundTag`, such as `getInt`, `getCompound` and `getList(name, type)`, plus `find` and
  `contains`.
- Added automatic compression detection: `NBTInputStream` without an `NBTCompression` now tells uncompressed, GZip,
  zlib and LZ4 input apart from its first bytes instead of assuming GZip, and reports the result through
  `getCompression()`. `NBTCompression.detect(bytes)` runs the same check on a byte array.
- Added a module descriptor: JNBT is the Java module `org.tectonicus.jnbt`.
- Added builders for compounds and lists (`CompoundTag.builder()`, `toBuilder()` and `ListTag.builder(type)`).
- `CompoundTag` rejects `TAG_End` entries, which cannot be written.
- Added Bedrock Edition support: `NBTFormat.BEDROCK` reads and writes little-endian NBT with UTF-8 strings, and
  `BedrockLevelDat` handles the `level.dat` header.
- Added SNBT output: `SNBT.toString(tag)` and `SNBT.toPrettyString(tag)`.

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
