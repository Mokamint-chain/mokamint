package io.hotmoka.spacemint.plotter.internal;

import io.hotmoka.spacemint.plotter.Deadline;

class DeadlineImpl implements Deadline {
	private final long progressive;
	private final byte[] value;

	DeadlineImpl(long progressive, byte[] value) {
		this.progressive = progressive;
		this.value = value;
	}

	@Override
	public int compareTo(Deadline other) {
		byte[] left = value, right = other.getValue();

		for (int i = 0; i < left.length; i++) {
			int a = left[i] & 0xff;
			int b = right[i] & 0xff;
			if (a != b)
				return a - b;
		}

		return 0; // deadlines with the same hashing algorithm have the same length
	}

	@Override
	public long getProgressive() {
		return progressive;
	}

	@Override
	public byte[] getValue() {
		return value.clone();
	}

	@Override
	public String toString() {
		return "nonce: " + progressive + ", deadline: " + bytesToHex(value);
	}

	private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

	private static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for (int j = 0; j < bytes.length; j++) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = HEX_ARRAY[v >>> 4];
	        hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
	    }
	    return new String(hexChars);
	}
}