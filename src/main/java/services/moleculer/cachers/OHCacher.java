package services.moleculer.cachers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.caffinitas.ohc.CacheSerializer;
import org.caffinitas.ohc.OHCache;
import org.caffinitas.ohc.OHCacheBuilder;

import io.datatree.Tree;
import io.datatree.dom.TreeWriterRegistry;
import services.moleculer.Promise;
import services.moleculer.ServiceBroker;
import services.moleculer.eventbus.GlobMatcher;
import services.moleculer.services.Name;
import services.moleculer.utils.Serializer;

import static services.moleculer.utils.CommonUtils.getProperty;
import static services.moleculer.utils.CommonUtils.compress;
import static services.moleculer.utils.CommonUtils.decompress;

/**
 * Off-heap cache implementation (it's similar to MemoryCacher, but stores
 * entries in the off-heap RAM). Requires "OHC" Windows/Linux/OSX off-heap
 * HashTable API (compile group: 'org.caffinitas.ohc', name: 'ohc-core-j8',
 * version: '0.6.1'). See this gitHub project for a more description:
 * https://github.com/snazy/ohc.<br>
 * Configuration properties:
 * <ul>
 * <li>ttl: expire time of entries in memory, in seconds (default: 0 = never
 * expires)
 * <li>capacity: capacity for data over the whole cache in MEGABYTES
 * <li>segmentCount: number of segments (must be a power of 2), defaults to
 * number-of-cores * 2
 * <li>hashTableSize: hash table size (must be a power of 2), defaults to 8192
 * <li>compressAbove: compress key and/or value above this size (BYTES)
 * <li>format: serializator type ("json", "smile", etc.)
 * </ul>
 * Performance:<br>
 * <br>
 * <b>Small uncompressed data</b><br>
 * In SMILE format: 392 000 gets / second<br>
 * In JSON format: 380 000 gets / second<br>
 * <br>
 * <b>Compressed large data in 20% compression ratio</b><br>
 * In SMILE format: 20 000 gets / second<br>
 * In JSON format: 16 000 gets / second<br>
 * <br>
 * This cache is fundamentally slower than MemoryCacher, but it can store
 * compressed entries in the off-heap RAM. OHCacher is the solution to store
 * huge amount of data in memory; if you plan to store few thousands (or less)
 * entries in the cache, use the faster MemoryCacher, otherwise use OHCacher.
 */
@Name("Off-heap Memory Cacher")
public final class OHCacher extends Cacher {

	// --- PROPERTIES ---

	/**
	 * Maximum capacity of whole cache in MEGABYTES
	 */
	private long capacity;

	/**
	 * Number of segments (must be a power of 2), defaults to number-of-cores *
	 * 2
	 */
	private int segmentCount;

	/**
	 * Hash table size (must be a power of 2), defaults to 8192
	 */
	private int hashTableSize;

	/**
	 * Expire time, in seconds (0 = never expires)
	 */
	private int ttl;

	/**
	 * Compress key and/or value above this size (BYTES)
	 */
	private int compressAbove = 1024;

	/**
	 * Serialization format
	 */
	private String format;

	// --- OFF-HEAP CACHE INSTANCE ---

	private OHCache<byte[], byte[]> cache;

	// --- CONSTRUCTORS ---

	/**
	 * Creates Off-heap Cacher with the default settings.
	 */
	public OHCacher() {
		this(0, 0, 0, 0, 1024, null);
	}

	/**
	 * Creates Off-heap Cacher.
	 * 
	 * @param capacity
	 *            capacity for data over the whole cache in MEGABYTES
	 * @param ttl
	 *            expire time of entries in memory, in seconds (default: 0 =
	 *            never expires)
	 */
	public OHCacher(long capacity, int ttl) {
		this(capacity, 0, 0, ttl, 1024, null);
	}

	/**
	 * Creates Off-heap Cacher.
	 * 
	 * @param capacity
	 *            capacity for data over the whole cache in MEGABYTES
	 * @param segmentCount
	 *            mumber of segments (must be a power of 2), defaults to
	 *            number-of-cores * 2
	 * @param hashTableSize
	 *            hash table size (must be a power of 2), defaults to 8192
	 * @param ttl
	 *            expire time of entries in memory, in seconds (default: 0 =
	 *            never expires)
	 * @param compressAbove
	 *            compress key and/or value above this size (in BYTES)
	 * @param format
	 *            serializator type ("json", "smile", etc.)
	 */
	public OHCacher(long capacity, int segmentCount, int hashTableSize, int ttl, int compressAbove, String format) {
		this.capacity = capacity;
		this.segmentCount = segmentCount;
		this.hashTableSize = hashTableSize;
		this.ttl = ttl;
		this.compressAbove = compressAbove;
		this.format = format;
	}

	// --- START CACHER ---

	/**
	 * Initializes cacher instance.
	 * 
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public final void start(ServiceBroker broker, Tree config) throws Exception {

		// Process config
		capacity = getProperty(config, "capacity", capacity).asInteger();
		segmentCount = getProperty(config, "segmentCount", segmentCount).asInteger();
		hashTableSize = getProperty(config, "hashTableSize", hashTableSize).asInteger();
		ttl = getProperty(config, "ttl", ttl).asInteger();
		compressAbove = getProperty(config, "compressAbove", compressAbove).asInteger();
		format = getProperty(config, "format", format).asString();

		if (format != null) {
			try {
				TreeWriterRegistry.getWriter(format);
			} catch (Exception e) {
				logger.warn("Unsupported format name (" + format + ")!");
				format = null;
			}
		}
		if (format == null) {
			try {
				TreeWriterRegistry.getWriter("smile");
				format = "smile";
			} catch (Exception e) {
				format = null;
			}
		}
		String formatName = format == null ? "JSON" : format.toUpperCase();
		logger.info("Cacher will use " + formatName + " format to serialize entries.");

		// Create cache
		OHCacheBuilder<byte[], byte[]> builder = OHCacheBuilder.newBuilder();
		if (capacity > 0) {

			// Capacity specified in MEGABYTES
			builder.capacity(capacity * 1024 * 1024);
		}
		if (segmentCount > 0) {
			builder.segmentCount(segmentCount);
		}
		if (hashTableSize > 0) {
			builder.hashTableSize(hashTableSize);
		}
		if (ttl > 0) {
			builder.defaultTTLmillis(ttl * 1000L);
			builder.timeouts(true);
			logger.info("Entries in cache expire after " + ttl + " seconds.");
		}
		logger.info("Maximum size of the cache is " + capacity + " Mbytes.");

		// Set serializers
		final ArraySerializer serializer = new ArraySerializer();
		builder.keySerializer(serializer);
		builder.valueSerializer(serializer);

		// Set scheduler
		builder.executorService(broker.components().scheduler());

		// Create cache
		cache = builder.throwOOME(true).build();
	}

	// --- CLOSE CACHE INSTANCE ---

	@Override
	public final void stop() {
		if (cache != null) {
			try {
				cache.close();
			} catch (Throwable ignored) {
			}
			cache = null;
		}
	}

	// --- IMPLEMENTED CACHE METHODS ---

	@Override
	public final Promise get(String key) {
		try {
			byte[] bytes = cache.get(keyToBytes(key));
			if (bytes == null) {
				return null;
			}
			return Promise.resolve(bytesToValue(bytes));
		} catch (Throwable cause) {
			logger.warn("Unable to read data from off-heap cache!", cause);
		}
		return null;
	}

	@Override
	public final void set(String key, Tree value) {
		try {
			if (value == null) {
				cache.remove(keyToBytes(key));
			} else {
				cache.put(keyToBytes(key), valueToBytes(value));
			}
		} catch (Throwable cause) {
			logger.warn("Unable to write data to off-heap cache!", cause);
		}
	}

	@Override
	public final void del(String key) {
		try {
			cache.remove(keyToBytes(key));
		} catch (Throwable cause) {
			logger.warn("Unable to delete data from off-heap cache!", cause);
		}
	}

	@Override
	public final void clean(String match) {
		try {
			if (match.isEmpty() || match.startsWith("*")) {
				cache.clear();
			} else if (match.indexOf('*') == -1) {
				cache.remove(keyToBytes(match));
			} else {
				Iterator<byte[]> i = cache.keyIterator();
				String key;
				while (i.hasNext()) {
					key = bytesToKey(i.next());
					if (GlobMatcher.matches(key, match)) {
						i.remove();
					}
				}
			}
		} catch (Throwable cause) {
			logger.warn("Unable to clean off-heap cache!", cause);
		}
	}

	// --- CACHE SERIALIZER ---

	private final byte[] keyToBytes(String key) throws Exception {
		int i = key.indexOf(':');

		byte[] part1;
		byte[] part2;
		boolean compressed;

		if (i == -1) {
			part1 = key.getBytes(StandardCharsets.UTF_8);
			part2 = new byte[0];
			compressed = false;
		} else {
			part1 = key.substring(0, i).getBytes(StandardCharsets.UTF_8);
			part2 = key.substring(i + 1).getBytes(StandardCharsets.UTF_8);
			if (compressAbove > 0 && part2.length > compressAbove) {
				part2 = compress(part2);
				compressed = true;
			} else {
				compressed = false;
			}
		}

		// Write key packet
		ByteArrayOutputStream out = new ByteArrayOutputStream(part1.length + part2.length + 16);
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeInt(part1.length);
		dos.write(part1);
		dos.writeInt(part2.length);
		dos.write(part2);
		dos.writeBoolean(compressed);
		dos.flush();

		// Return key as partly compressed bytes
		return out.toByteArray();
	}

	private final String bytesToKey(byte[] bytes) throws Exception {

		// Read key packet
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		DataInputStream dis = new DataInputStream(in);
		int len = dis.readInt();
		byte[] part1 = new byte[len];
		if (len > 0) {
			dis.readFully(part1);
		}

		// Return the first part of the key
		return new String(part1, StandardCharsets.UTF_8);
	}

	private final byte[] valueToBytes(Tree tree) throws Exception {

		// Compress content
		byte[] part1 = Serializer.serialize(tree, format);
		boolean compressed;
		if (compressAbove > 0 && part1.length > compressAbove) {
			part1 = compress(part1);
			compressed = true;
		} else {
			compressed = false;
		}

		// Write value packet
		ByteArrayOutputStream out = new ByteArrayOutputStream(part1.length + 8);
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeInt(part1.length);
		dos.write(part1);
		dos.writeBoolean(compressed);
		dos.flush();

		// Return value as compressed bytes
		return out.toByteArray();
	}

	private final Tree bytesToValue(byte[] bytes) throws Exception {

		// Read value packet
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		DataInputStream dis = new DataInputStream(in);
		int len = dis.readInt();
		byte[] part1 = new byte[len];
		if (len > 0) {
			dis.readFully(part1);
			if (dis.readBoolean()) {
				part1 = decompress(part1);
			}
		}

		// Return value
		return Serializer.deserialize(part1, format);
	}

	private static final class ArraySerializer implements CacheSerializer<byte[]> {

		@Override
		public final int serializedSize(byte[] value) {
			return value.length;
		}

		@Override
		public final void serialize(byte[] value, ByteBuffer buf) {
			buf.put(value);
		}

		@Override
		public final byte[] deserialize(ByteBuffer buf) {
			int len = buf.remaining();
			byte[] bytes = new byte[len];
			buf.get(bytes, 0, len);
			return bytes;
		}

	}
	
	// --- GETTERS / SETTERS ---
	
	public final long getCapacity() {
		return capacity;
	}

	public final void setCapacity(long capacity) {
		this.capacity = capacity;
	}

	public final int getSegmentCount() {
		return segmentCount;
	}

	public final void setSegmentCount(int segmentCount) {
		this.segmentCount = segmentCount;
	}

	public final int getHashTableSize() {
		return hashTableSize;
	}

	public final void setHashTableSize(int hashTableSize) {
		this.hashTableSize = hashTableSize;
	}

	public final int getTtl() {
		return ttl;
	}

	public final void setTtl(int ttl) {
		this.ttl = ttl;
	}

	public final int getCompressAbove() {
		return compressAbove;
	}

	public final void setCompressAbove(int compressAbove) {
		this.compressAbove = compressAbove;
	}

	public final String getFormat() {
		return format;
	}

	public final void setFormat(String format) {
		this.format = format;
	}
	
}