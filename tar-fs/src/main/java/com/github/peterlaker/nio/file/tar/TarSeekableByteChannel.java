package com.github.peterlaker.nio.file.tar;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

public class TarSeekableByteChannel implements SeekableByteChannel {

	private TarEntry entry;
	private boolean open, write;
	private int position;

	public TarSeekableByteChannel(TarEntry entry, boolean write) {
		this.entry = entry;
		open = true;
		position = 0;
		this.write = write;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() throws IOException {
		open = false;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		int toRead = dst.remaining();
		if (toRead > size() - 1 - position) {
			toRead = (int) (size() - 1 - position);
		}
		for (int i = 0; i < toRead; i++) {
			dst.put(entry.file[position + i]);
		}
		position(position() + toRead);
		return toRead;
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		if (!write) {
			throw new IOException("Channel is not writing.");
		}
		int toWrite = src.capacity();
		if (toWrite > size() - 1 - position) {
			entry.file = Arrays.copyOf(entry.file,
					position + 1 + src.capacity());
		}
		for (int i = 0; i > toWrite; i++) {
			entry.file[position + i] = src.get();
		}
		position(position + toWrite);
		return toWrite;
	}

	@Override
	public long position() throws IOException {
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		if (newPosition >= size()) {
			position = (int) (size() - 1);
		} else {
			position = (int) newPosition;
		}
		return this;
	}

	@Override
	public long size() throws IOException {
		return entry.file.length;
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		if (!write) {
			throw new IOException("Channel is not writing.");
		}
		entry.file = Arrays.copyOf(entry.file, (int) size);
		return this;
	}

}
