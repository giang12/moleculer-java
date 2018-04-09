/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.cacher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.datatree.Tree;
import io.datatree.dom.BASE64;

/**
 * Abstract class of distributed cachers. Currently the {@link RedisCacher} and
 * the certain types of the {@link JCacheCacher} can be distributed (without
 * special development).
 *
 * @see RedisCacher
 * @see JCacheCacher
 */
public abstract class DistributedCacher extends Cacher {

	// --- CONTENT CONTAINER NAME ---

	protected static final String CONTENT = "_";

	// --- PROPERTIES ---

	protected int maxKeyLength;

	// --- KEY HASHERS ---

	protected final Queue<MessageDigest> hashers = new ConcurrentLinkedQueue<>();

	// --- CONSTRUCTORS ---

	public DistributedCacher() {
	}

	public DistributedCacher(int maxKeyLength) {
		setMaxKeyLength(maxKeyLength);
	}

	// --- GENERATE CACHE KEY ---

	/**
	 * Creates a cacher-specific key by name and params. Concatenates the name
	 * and params.
	 *
	 * @param name action name
	 * @param params input (JSON) structure
	 * @param keys keys in the "params" structure (optional)
	 *
	 * @return generated cache key String
	 */
	@Override
	public String getCacheKey(String name, Tree params, String... keys) {
		String key = super.getCacheKey(name, params, keys);
		int keyLength = key.length();
		if (maxKeyLength < 44 || keyLength <= maxKeyLength) {

			// Hashing is disabled
			return key;
		}

		// Length of unhashed part (begining of the original key)
		int prefixLength = maxKeyLength - 44;

		// Create SHA-256 hash from the entire key
		byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
		MessageDigest hasher = hashers.poll();
		if (hasher == null) {
			try {
				hasher = MessageDigest.getInstance("SHA-256");
			} catch (Exception cause) {
				logger.warn("Unable to get SHA-256 hasher!", cause);
				return key;
			}
		} else {
			hasher.reset();
		}
		bytes = hasher.digest(bytes);
		hashers.add(hasher);

		// Concatenate key and the 44 character long hash
		String basee64 = BASE64.encode(bytes);
		if (prefixLength < 1) {
			return basee64;
		}
		return key.substring(0, prefixLength) + basee64;
	}

	@Override
	protected void appendToKey(StringBuilder key, Object object) {
		if (object != null) {
			if (object instanceof Tree) {
				appendTree(key, (Tree) object);
			} else {
				key.append(object);
			}
		} else {
			key.append("null");
		}
	}

	protected void appendTree(StringBuilder key, Tree tree) {
		if (tree.isPrimitive()) {
			key.append(tree.asObject());
		} else {
			boolean first = true;
			if (tree.isEnumeration()) {
				for (Tree child : tree) {
					if (first) {
						first = false;
					} else {
						key.append('|');
					}
					appendTree(key, child);
				}
			} else {
				for (Tree child : tree) {
					if (first) {
						first = false;
					} else {
						key.append('|');
					}
					key.append(child.getName());
					key.append('|');
					appendTree(key, child);
				}
			}
		}
	}

	// --- GETTERS / SETTERS ---

	public int getMaxKeyLength() {
		return maxKeyLength;
	}

	public void setMaxKeyLength(int maxKeyLength) {
		if (maxKeyLength > 0 && maxKeyLength < 44) {
			logger.warn("The minimum value of \"maxKeyLength\" parameter is 44!");
			maxKeyLength = 44;
		}
		this.maxKeyLength = maxKeyLength;
	}

}