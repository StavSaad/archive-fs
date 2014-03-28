package com.github.peterlaker.nio.file.tar;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class TarFileStore extends FileStore {

	private final AbstractTarFileSystem tfs;

	TarFileStore(TarPath tpath) {
		tfs = tpath.getFileSystem();
	}

	@Override
	public String name() {
		return tfs.toString() + "/";
	}

	@Override
	public String type() {
		return "tar";
	}

	@Override
	public boolean isReadOnly() {
		return tfs.isReadOnly();
	}

	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return (type == BasicFileAttributeView.class ||
				type == TarFileAttributeView.class);
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return name.equals("basic") || name.equals("tar");
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		if (type == null) {
			throw new NullPointerException();
		}
		return null;
	}

	@Override
	public long getTotalSpace() throws IOException {
		return new TarFileStoreAttributes(this).totalSpace();
	}

	@Override
	public long getUsableSpace() throws IOException {
		return new TarFileStoreAttributes(this).usableSpace();
	}

	@Override
	public long getUnallocatedSpace() throws IOException {
		return new TarFileStoreAttributes(this).unallocatedSpace();
	}

	@Override
	public Object getAttribute(String attribute) throws IOException {
		if (attribute.equals("totalSpace")) {
			return getTotalSpace();
		}
		if (attribute.equals("usableSpace")) {
			return getUsableSpace();
		}
		if (attribute.equals("unallocatedSpace")) {
			return getUnallocatedSpace();
		}
		throw new UnsupportedOperationException("does not support the given attribute");
	}

	private static class TarFileStoreAttributes {
		final FileStore fstore;
		final long size;

		public TarFileStoreAttributes(TarFileStore fileStore)
				throws IOException
		{
			Path path = FileSystems.getDefault().getPath(fileStore.name());
			size = Files.size(path);
			fstore = Files.getFileStore(path);
		}

		public long totalSpace() {
			return size;
		}

		public long usableSpace() throws IOException {
			if (!fstore.isReadOnly()) {
				return fstore.getUsableSpace();
			}
			return 0;
		}

		public long unallocatedSpace()  throws IOException {
			if (!fstore.isReadOnly()) {
				return fstore.getUnallocatedSpace();
			}
			return 0;
		}
	}
}
