package com.github.peterlaker.nio.file.tar;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class TarFileAttributeView implements BasicFileAttributeView
{
	private static enum AttrID {
		size,
		lastModifiedTime,
		isDirectory,
	};

	private final TarPath path;
	private final boolean isZipView;

	private TarFileAttributeView(TarPath path, boolean isZipView) {
		this.path = path;
		this.isZipView = isZipView;
	}

	@SuppressWarnings("unchecked")
	static <V extends FileAttributeView> V get(TarPath path, Class<V> type) {
		if (type == null) {
			throw new NullPointerException();
		}
		if (type == BasicFileAttributeView.class) {
			return (V)new TarFileAttributeView(path, false);
		}
		if (type == TarFileAttributeView.class) {
			return (V)new TarFileAttributeView(path, true);
		}
		return null;
	}

	static TarFileAttributeView get(TarPath path, String type) {
		if (type == null) {
			throw new NullPointerException();
		}
		if (type.equals("basic")) {
			return new TarFileAttributeView(path, false);
		}
		if (type.equals("zip")) {
			return new TarFileAttributeView(path, true);
		}
		return null;
	}

	@Override
	public String name() {
		return isZipView ? "zip" : "basic";
	}

	@Override
	public TarFileAttributes readAttributes() throws IOException
	{
		return path.getAttributes();
	}

	@Override
	public void setTimes(FileTime lastModifiedTime,
			FileTime lastAccessTime,
			FileTime createTime)
					throws IOException
	{
		path.setTimes(lastModifiedTime, lastAccessTime, createTime);
	}

	void setAttribute(String attribute, Object value)
			throws IOException
	{
		try {
			if (AttrID.valueOf(attribute) == AttrID.lastModifiedTime) {
				setTimes (null, null, (FileTime)value);
			}
			return;
		} catch (IllegalArgumentException x) {}
		throw new UnsupportedOperationException("'" + attribute +
				"' is unknown or read-only attribute");
	}

	Map<String, Object> readAttributes(String attributes)
			throws IOException
			{
		TarFileAttributes zfas = readAttributes();
		LinkedHashMap<String, Object> map = new LinkedHashMap<>();
		if ("*".equals(attributes)) {
			for (AttrID id : AttrID.values()) {
				try {
					map.put(id.name(), attribute(id, zfas));
				} catch (IllegalArgumentException x) {}
			}
		} else {
			String[] as = attributes.split(",");
			for (String a : as) {
				try {
					map.put(a, attribute(AttrID.valueOf(a), zfas));
				} catch (IllegalArgumentException x) {}
			}
		}
		return map;
			}

	Object attribute(AttrID id, TarFileAttributes zfas) {
		switch (id) {
		case size:
			return zfas.size();
		case lastModifiedTime:
			return zfas.lastModifiedTime();
		case isDirectory:
			return zfas.isDirectory();
		}
		return null;
	}
}
