package io.mokamint.nonce.internal;

import java.util.Arrays;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Nonce;

public class DeadlineImpl implements Deadline {
	private final byte[] prolog;
	private final long progressive;
	private final byte[] value;
	private final int scoopNumber;
	private final byte[] data;
	private final HashingAlgorithm<byte[]> hashing;

	public DeadlineImpl(byte[] prolog, long progressive, byte[] value, int scoopNumber, byte[] data, HashingAlgorithm<byte[]> hashing) {
		this.prolog = prolog;
		this.progressive = progressive;
		this.value = value;
		this.scoopNumber = scoopNumber;
		this.data = data;
		this.hashing = hashing;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Deadline) {
			Deadline otherAsDeadline = (Deadline) other;
			return progressive == otherAsDeadline.getProgressive() &&
				scoopNumber == otherAsDeadline.getScoopNumber() &&
				Arrays.equals(value, otherAsDeadline.getValue()) &&
				Arrays.equals(prolog, otherAsDeadline.getProlog()) &&
				Arrays.equals(data, otherAsDeadline.getData()) &&
				hashing.getName().equals(otherAsDeadline.getHashing().getName());
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return ((int) progressive) ^ scoopNumber ^ Arrays.hashCode(value); 
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
	public int getScoopNumber() {
		return scoopNumber;
	}

	@Override
	public byte[] getData() {
		return data.clone();
	}

	@Override
	public HashingAlgorithm<byte[]> getHashing() {
		return hashing;
	}

	@Override
	public Nonce toNonce() {
		return Nonces.of(prolog, progressive, hashing);
	}

	@Override
	public boolean isValid() {
		return this.equals(toNonce().getDeadline(scoopNumber, data));
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(data) + ", nonce: " + progressive + ", value: " + Hex.toHexString(value);
	}
}