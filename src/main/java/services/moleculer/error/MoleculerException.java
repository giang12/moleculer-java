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
package services.moleculer.error;

import io.datatree.Tree;

/**
 * The base error class.
 */
public abstract class MoleculerException extends Exception {

	// --- SERIAL VERSION UID ---

	private static final long serialVersionUID = 8248260686740319502L;

	// --- PROPERTIES ---

	protected final boolean retriable;
	protected final int code;
	protected final Tree data;

	// --- CONSTRUCTOR ---

	public MoleculerException(boolean retriable, String message, int code, Tree data) {
		super(message);
		this.retriable = retriable;
		this.code = code;
		this.data = data;
	}

	// --- PROPERTY GETTERS ---

	public int getCode() {
		return code;
	}

	public Tree getData() {
		return data;
	}

	public boolean isRetriable() {
		return retriable;
	}

}