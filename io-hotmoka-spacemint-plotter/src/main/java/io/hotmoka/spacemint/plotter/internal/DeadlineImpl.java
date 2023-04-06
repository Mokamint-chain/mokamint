package io.hotmoka.spacemint.plotter.internal;

import java.util.Arrays;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.spacemint.miner.api.Deadline;
import io.hotmoka.spacemint.plotter.Nonce;

class DeadlineImpl implements Deadline {
	private final byte[] prolog;
	private final long progressive;
	private final byte[] value;
	private final HashingAlgorithm<byte[]> hashing;

	DeadlineImpl(byte[] prolog, long progressive, byte[] value, HashingAlgorithm<byte[]> hashing) {
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.hashing = hashing;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Deadline) {
			Deadline otherAsDeadline = (Deadline) other;
			return progressive == otherAsDeadline.getProgressive() &&
				Arrays.equals(value, otherAsDeadline.getValue()) &&
				Arrays.equals(prolog, otherAsDeadline.getProlog()) &&
				hashing.getName().equals(otherAsDeadline.getHashing().getName());
		}
		else
			return false;
	}

	@Override
	public int compareByValue(Deadline other) {
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
	public byte[] getProlog() {
		return prolog.clone();
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
	public HashingAlgorithm<byte[]> getHashing() {
		return hashing;
	}

	@Override
	public Nonce toNonce() {
		return Nonce.of(prolog, progressive, hashing);
	}

	@Override
	public String toString() {
		return "nonce: " + progressive + ", deadline: " + Hex.toHexString(value);
	}
}