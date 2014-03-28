package com.github.peterlaker.nio.file.tar;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

public abstract class AbstractTarFileSystem extends FileSystem {

	private final AbstractTarFileSystemProvider provider;
	private final TarPath defaultdir;
	private boolean readOnly = false;
	private final Path tfpath;
	private final List<OutputStream> outputStreams;
	private Map<TarEntry, byte[]> entriesToData;

	// configurable by env map
	private final String defaultDir; // default dir for the file system
	private final boolean createNew; // create a new tar if not exists

	protected AbstractTarFileSystem(AbstractTarFileSystemProvider provider, Path tfpath,
			Map<String, ?> env) throws IOException {
		// configurable env setup
		createNew = "true".equals(env.get("create"));
		defaultDir = env.containsKey("default.dir") ? (String) env
				.get("default.dir") : "/";
				entriesToData = new HashMap<>();
				if (defaultDir.charAt(0) != '/') {
					throw new IllegalArgumentException("default dir should be absolute");
				}
				this.provider = provider;
				this.tfpath = tfpath;
				if (Files.notExists(tfpath)) {
					if (!createNew) {
						throw new FileSystemNotFoundException(tfpath.toString());
					}
				}
				// sm and existence check
				tfpath.getFileSystem().provider().checkAccess(tfpath, AccessMode.READ);
				if (!Files.isWritable(tfpath)) {
					readOnly = true;
				}
				defaultdir = new TarPath(this, defaultDir.getBytes());
				outputStreams = new ArrayList<>();
				mapEntries();
	}

	protected abstract byte[] readFile(Path path) throws IOException;

	private void mapEntries() throws IOException {
		beginRead();
		try {
			entriesToData.clear();
			byte[] tfByteArray;
			if(Files.notExists(tfpath)) {
				tfByteArray = new byte[TarConstants.DATA_BLOCK];
			} else {
				tfByteArray = readFile(tfpath);
			}
			int numOfBlocks = (int) Math.ceil((double) tfByteArray.length
					/ TarConstants.DATA_BLOCK) - 1; // discard the EOF block
			for (int i = 0; i < numOfBlocks; i++) {
				byte[] block = Arrays.copyOfRange(tfByteArray, i
						* TarConstants.DATA_BLOCK, (i)
						* TarConstants.DATA_BLOCK + TarConstants.DATA_BLOCK);
				byte[] magic = Arrays.copyOfRange(block, TarConstants.MAGICOFF,
						TarConstants.MAGICOFF + TarConstants.MAGICLEN - 1);
				if (new String(magic).equals("ustar")) {
					TarEntry te = new TarEntry(block);
					byte[] data = Arrays.copyOfRange(tfByteArray, (i+1)*TarConstants.DATA_BLOCK, (int) ((i+1)*TarConstants.DATA_BLOCK+te.getSize()));
					entriesToData.put(te, data);
					int blocksNeeded = (int) Math.ceil((double) te.getSize()
							/ TarConstants.DATA_BLOCK);
					for (int j = 0; j < blocksNeeded; j++) {
						i++;
					}
				}
			}
		} finally {
			endRead();
		}
	}

	@Override
	public FileSystemProvider provider() {
		return provider;
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public boolean isReadOnly() {
		return readOnly;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		ArrayList<Path> pathArr = new ArrayList<>();
		pathArr.add(new TarPath(this, new byte[] { '/' }));
		return pathArr;
	}

	TarPath getDefaultDir() { // package private
		return defaultdir;
	}

	@Override
	public TarPath getPath(String first, String... more) {
		String path;
		if (more.length == 0) {
			path = first;
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(first);
			for (String segment : more) {
				if (segment.length() > 0) {
					if (sb.length() > 0) {
						sb.append('/');
					}
					sb.append(segment);
				}
			}
			path = sb.toString();
		}
		return new TarPath(this, path.getBytes());
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() {
		throw new UnsupportedOperationException();
	}

	FileStore getFileStore(TarPath path) {
		return new TarFileStore(path);
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		ArrayList<FileStore> list = new ArrayList<>(1);
		list.add(new TarFileStore(new TarPath(this, new byte[] { '/' })));
		return list;
	}

	private static final Set<String> supportedFileAttributeViews = Collections
			.unmodifiableSet(new HashSet<String>(Arrays.asList("basic", "tar")));

	@Override
	public Set<String> supportedFileAttributeViews() {
		return supportedFileAttributeViews;
	}

	@Override
	public String toString() {
		return tfpath.toString();
	}

	Path getTarFile() {
		return tfpath;
	}

	private static final String GLOB_SYNTAX = "glob";
	private static final String REGEX_SYNTAX = "regex";

	@Override
	public PathMatcher getPathMatcher(String syntaxAndInput) {
		int pos = syntaxAndInput.indexOf(':');
		if (pos <= 0 || pos == syntaxAndInput.length()) {
			throw new IllegalArgumentException();
		}
		String syntax = syntaxAndInput.substring(0, pos);
		String input = syntaxAndInput.substring(pos + 1);
		String expr;
		if (syntax.equals(GLOB_SYNTAX)) {
			expr = TarUtils.toRegexPattern(input);
		} else {
			if (syntax.equals(REGEX_SYNTAX)) {
				expr = input;
			} else {
				throw new UnsupportedOperationException("Syntax '" + syntax
						+ "' not recognized");
			}
		}
		// return matcher
		final Pattern pattern = Pattern.compile(expr);
		return new PathMatcher() {
			@Override
			public boolean matches(Path path) {
				return pattern.matcher(path.toString()).matches();
			}
		};
	}

	@Override
	public void close() throws IOException {
		beginWrite();
		try {
			if (!isOpen) {
				return;
			}
			isOpen = false; // set closed
		} finally {
			endWrite();
		}
		beginWrite();
		try {
			for (OutputStream os : outputStreams) {
				os.close();
			}
		} finally {
			endWrite();
		}
		beginWrite();
		try {
			if(Files.notExists(tfpath)) {
				Files.createDirectories(tfpath.getParent());
				Files.createFile(tfpath);
			}
			writeFile(getTarBytes(), tfpath);
		} finally {
			endWrite();
		}
		provider.removeFileSystem(tfpath, this);
	}

	private byte[] getTarBytes() {
		int bytesNeeded = 0;
		for(Entry<TarEntry, byte[]> entry : entriesToData.entrySet()) {
			bytesNeeded += TarConstants.HEADER_BLOCK;
			int dataBlocks = (int) Math.ceil((double) entry.getKey().getSize() / TarConstants.DATA_BLOCK);
			bytesNeeded += TarConstants.DATA_BLOCK * dataBlocks;
		}
		bytesNeeded += TarConstants.DATA_BLOCK;
		byte[] tar = new byte[bytesNeeded];
		int offset = 0;
		for(Entry<TarEntry, byte[]> entry : entriesToData.entrySet()) {
			byte[] header = new byte[TarConstants.HEADER_BLOCK];
			entry.getKey().writeEntryHeader(header);
			for(int i = 0; i < header.length; i++) {
				tar[offset + i] = header[i];
			}
			offset += TarConstants.HEADER_BLOCK;
			int dataSize = (int) Math.ceil((double) entry.getKey().getSize() / TarConstants.DATA_BLOCK);
			for(int i = 0; i < entry.getValue().length; i++) {
				tar[offset + i] = entry.getValue()[i];
			}
			offset += dataSize*TarConstants.DATA_BLOCK;
		}
		return tar;
	}

	protected abstract void writeFile(byte[] tarBytes, Path outPath) throws IOException;

	private final void beginWrite() {
		rwlock.writeLock().lock();
	}

	private final void endWrite() {
		rwlock.writeLock().unlock();
	}

	private final void beginRead() {
		rwlock.readLock().lock();
	}

	private final void endRead() {
		rwlock.readLock().unlock();
	}

	private volatile boolean isOpen = true;

	private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

	@Override
	protected void finalize() throws IOException {
		close();
	}

	public Iterator<Path> iteratorOf(byte[] path,
			java.nio.file.DirectoryStream.Filter<? super Path> filter)
					throws IOException {
		Collection<Path> subPaths = new ArrayList<>();
		String pathString = new String(path);
		for (TarEntry te : entriesToData.keySet()) {
			String name = te.getName();
			if (name.startsWith(pathString) && !name.equals(pathString)) {
				subPaths.add(getPath(name));
			}
		}
		return subPaths.iterator();
	}

	public boolean isDirectory(byte[] path) {
		TarEntry te = getTarEntryFromPath(path);
		return te.isDirectory();
	}

	private TarEntry getTarEntryFromPath(byte[] path) {
		TarEntry te = null;
		String name = new String(path);
		beginRead();
		try {
			for(TarEntry e : entriesToData.keySet()) {
				if(e.getName().equals(name)) {
					te = e;
				}
			}
		} catch (NullPointerException e) {
		} finally {
			endRead();
		}
		return te;
	}

	public void createDirectory(byte[] resolvedPath, FileAttribute<?>[] attrs) {
		TarHeader th = TarHeader.createHeader(new String(resolvedPath), 0,
				System.currentTimeMillis(), true);
		TarEntry te = new TarEntry(th);
		addEntry(te, new byte[0]);
	}

	private void addEntry(TarEntry te, byte[] data) {
		beginWrite();
		try {
			entriesToData.put(te, data);
		} finally {
			endWrite();
		}
	}

	public InputStream newInputStream(byte[] resolvedPath) {
		byte[] data = null;
		beginRead();
		try {
			data = getDataBytes(resolvedPath);
		} finally {
			endRead();
		}
		return new ByteArrayInputStream(data);
	}

	public void deleteFile(byte[] resolvedPath, boolean failIfNotExists) throws FileNotFoundException {
		TarEntry te = getTarEntryFromPath(resolvedPath);
		if(failIfNotExists && te == null) {
			throw new FileNotFoundException();
		}
		entriesToData.remove(te);
	}

	public TarFileAttributes getFileAttributes(byte[] resolvedPath) {
		return new TarFileAttributes(getTarEntryFromPath(resolvedPath));
	}

	public void setTimes(byte[] resolvedPath, FileTime mtime, FileTime atime,
			FileTime ctime) {
		TarEntry te = getTarEntryFromPath(resolvedPath);
		te.setModTime(mtime.toMillis());
	}

	public SeekableByteChannel newByteChannel(byte[] resolvedPath,
			Set<? extends OpenOption> options, FileAttribute<?>[] attrs) {
		throw new UnsupportedOperationException();
	}

	public FileChannel newFileChannel(byte[] resolvedPath,
			Set<? extends OpenOption> options, FileAttribute<?>[] attrs) {
		throw new UnsupportedOperationException();
	}

	public boolean exists(byte[] resolvedPath) {
		return getTarEntryFromPath(resolvedPath) != null;
	}

	public OutputStream newOutputStream(final byte[] resolvedPath,
			OpenOption... options) throws IOException {
		final ArrayList<Byte> bytesWritten = new ArrayList<>();
		List<OpenOption> opts = Arrays.asList(options);
		if (exists(resolvedPath)) {
			if (opts.contains(StandardOpenOption.APPEND)) {
				byte[] data = getDataBytes(resolvedPath);
				for (int i = 0; i < data.length; i++) {
					bytesWritten.add(data[i]);
				}
			}
			if (opts.contains(StandardOpenOption.CREATE_NEW)) {
				throw new FileAlreadyExistsException(new String(resolvedPath));
			}
		} else {
			if (!opts.contains(StandardOpenOption.CREATE)
					&& !opts.contains(StandardOpenOption.CREATE_NEW)) {
				throw new FileNotFoundException(new String(resolvedPath));
			}
		}
		OutputStream os = new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				bytesWritten.add((byte) b);
			}

			@Override
			public void close() throws IOException {
				byte[] data = new byte[bytesWritten.size()];
				TarEntry e = new TarEntry(TarHeader.createHeader(new String(
						resolvedPath), bytesWritten.size(), System
						.currentTimeMillis(), false));
				for (int i = 0; i < bytesWritten.size(); i++) {
					data[i] = bytesWritten.get(i);
				}
				if (exists(resolvedPath)) {
					deleteFile(resolvedPath, true);
				}
				addEntry(e, data);
			}
		};
		outputStreams.add(os);
		return os;
	}

	private byte[] getDataBytes(byte[] path) {
		TarEntry te = getTarEntryFromPath(path);
		return entriesToData.get(te);
	}

	public void copyFile(boolean deleteSourceFile, byte[] srcPath,
			byte[] targetPath, CopyOption... options) throws IOException {
		List<CopyOption> opts = Arrays.asList(options);
		if (!exists(srcPath)) {
			throw new FileNotFoundException();
		}
		if (exists(targetPath)
				&& !opts.contains(StandardCopyOption.REPLACE_EXISTING)) {
			throw new FileAlreadyExistsException(new String(targetPath));
		}
		beginWrite();
		try {
			TarEntry srcEntry = getTarEntryFromPath(srcPath);
			byte[] data = entriesToData.get(srcEntry);
			if (exists(targetPath)) {
				deleteFile(targetPath, true);
			}
			TarEntry targetEntry = new TarEntry(TarHeader.createHeader(
					new String(targetPath), data.length, srcEntry.getModTime()
					.getTime(), srcEntry.isDirectory()));
			addEntry(targetEntry, data);
		} finally {
			endWrite();
		}
	}

}
