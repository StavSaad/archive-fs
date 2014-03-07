package il.peterlaker.path.spi.tar;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

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
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

public class TarFileSystem extends FileSystem {

	private final TarFileSystemProvider provider;
	private final TarPath defaultdir;
	private boolean readOnly = false;
	private final Path tfpath;
	private Map<String, Integer> entryHeadersOffsets;
	private byte[] tfByteArray;

	// configurable by env map
	private final String defaultDir; // default dir for the file system
	private final boolean createNew; // create a new tar if not exists

	TarFileSystem(TarFileSystemProvider provider, Path tfpath,
			Map<String, ?> env) throws IOException {
		// configurable env setup
		this.entryHeadersOffsets = new HashMap<>();
		this.createNew = "true".equals(env.get("create"));
		this.defaultDir = env.containsKey("default.dir") ? (String) env
				.get("default.dir") : "/";
				if (this.defaultDir.charAt(0) != '/')
					throw new IllegalArgumentException("default dir should be absolute");

				this.provider = provider;
				this.tfpath = tfpath;
				if (Files.notExists(tfpath)) {
					if (createNew) {
						try (OutputStream os = Files.newOutputStream(tfpath,
								CREATE_NEW, WRITE)) {
							os.write(new byte[TarConstants.DATA_BLOCK]);
						}
					} else {
						throw new FileSystemNotFoundException(tfpath.toString());
					}
				}
				// sm and existence check
				tfpath.getFileSystem().provider().checkAccess(tfpath, AccessMode.READ);
				if (!Files.isWritable(tfpath))
					this.readOnly = true;
				this.defaultdir = new TarPath(this, defaultDir.getBytes());
				this.tfByteArray = Files.readAllBytes(tfpath);
				this.ch = Files.newByteChannel(tfpath, READ);
				mapEntries();
	}

	private void mapEntries() {
		beginRead();
		try {
			this.entryHeadersOffsets.clear();
			int numOfBlocks = (int) Math.ceil((double) tfByteArray.length / TarConstants.DATA_BLOCK) - 1; //discard the EOF block
			for(int i = 0; i < numOfBlocks; i++) {
				byte[] block = Arrays.copyOfRange(tfByteArray, i*TarConstants.DATA_BLOCK, (i)*TarConstants.DATA_BLOCK + TarConstants.DATA_BLOCK);
				byte[] magic = Arrays.copyOfRange(block, TarConstants.MAGICOFF, TarConstants.MAGICOFF+TarConstants.MAGICLEN-1);
				if(new String(magic).equals("ustar")) {
					TarEntry te = new TarEntry(block);
					String name =te.getName();
					int blocksNeeded = (int) Math.ceil((double) te.getSize() / TarConstants.DATA_BLOCK);
					for(int j = 0; j < blocksNeeded; j++) {
						i++;
					}
					this.entryHeadersOffsets.put(name, i*TarConstants.DATA_BLOCK);
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
					if (sb.length() > 0)
						sb.append('/');
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
			if (!isOpen)
				return;
			isOpen = false; // set closed
		} finally {
			endWrite();
		}
		beginWrite(); // lock and sync
		try {
			ch.close(); // close the ch just in case no update
		} finally { // and sync dose not close the ch
			endWrite();
		}
		beginWrite();
		try {
			Files.write(tfpath, tfByteArray, StandardOpenOption.WRITE);
		} finally {
			endWrite();
		}
		provider.removeFileSystem(tfpath, this);
	}

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
	private final SeekableByteChannel ch; // channel to the tarfile

	private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

	protected void finalize() throws IOException {
		close();
	}

	public Iterator<Path> iteratorOf(byte[] path,
			java.nio.file.DirectoryStream.Filter<? super Path> filter) throws IOException {
		Collection<Path> subPaths = new ArrayList<>();
		String pathString = new String(path);
		for(String name : entryHeadersOffsets.keySet()) {
			if(name.startsWith(pathString) && !name.equals(pathString)) {
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
			int offset = entryHeadersOffsets.get(name);
			te = new TarEntry(Arrays.copyOfRange(tfByteArray, offset, offset+TarConstants.DATA_BLOCK));
		} catch (NullPointerException e) {
		} finally {
			endRead();
		}
		return te;
	}

	public void createDirectory(byte[] resolvedPath, FileAttribute<?>[] attrs) {
		TarHeader th = TarHeader.createHeader(new String(resolvedPath), 0, System.currentTimeMillis(), true);
		TarEntry te = new TarEntry(th);
		byte[] entry = new byte[TarConstants.HEADER_BLOCK];
		te.writeEntryHeader(entry);
		addEntryToByteArray(entry);
	}

	private void addEntryToByteArray(byte[] entry) {
		beginWrite();
		try {
			byte[] newArr = Arrays.copyOf(tfByteArray, tfByteArray.length+entry.length);
			for(int i = 0; i < entry.length; i++) {
				newArr[tfByteArray.length-TarConstants.DATA_BLOCK+i] = entry[i];
			}
			this.tfByteArray = newArr;
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

	public void deleteFile(byte[] resolvedPath, boolean failIfNotExists) {
		TarEntry te = getTarEntryFromPath(resolvedPath);
		long size = te.getSize();
		int dataBlocks = (int) Math.ceil((double) size / TarConstants.DATA_BLOCK);
		int overAllLength = (dataBlocks+1)*TarConstants.DATA_BLOCK;
		truncateEntry(entryHeadersOffsets.get(te.getName()), overAllLength);
	}

	private void truncateEntry(Integer offset, int length) {
		beginWrite();
		try {
			byte[] newArr = new byte[tfByteArray.length - length];
			byte[] prev = Arrays.copyOfRange(tfByteArray, 0, offset);
			byte[] suff = Arrays.copyOfRange(tfByteArray, offset+length, tfByteArray.length);
			for(int i = 0; i < prev.length; i++) {
				newArr[i] = prev[i];
			}
			for(int i = 0; i < suff.length; i++) {
				newArr[prev.length+i] = suff[i];
			}
			this.tfByteArray = newArr;
		} finally {
			endWrite();
		}
	}

	public TarFileAttributes getFileAttributes(byte[] resolvedPath) {
		return new TarFileAttributes(getTarEntryFromPath(resolvedPath));
	}

	public void setTimes(byte[] resolvedPath, FileTime mtime, FileTime atime,
			FileTime ctime) {
		TarEntry te = getTarEntryFromPath(resolvedPath);
		te.setModTime(mtime.toMillis());
		updateEntryHeader(te);
	}

	private void updateEntryHeader(TarEntry te) {
		byte[] newHeaderEntry = new byte[TarConstants.HEADER_BLOCK];
		te.writeEntryHeader(newHeaderEntry);
		int offset = entryHeadersOffsets.get(te.getName());
		beginWrite();
		try {
			for(int i = 0; i < newHeaderEntry.length; i++) {
				tfByteArray[offset+i] = newHeaderEntry[i];
			}
		} finally {
			endWrite();
		}
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
		if(exists(resolvedPath)) {
			if(opts.contains(StandardOpenOption.APPEND)) {
				byte[] data = getDataBytes(resolvedPath);
				for(int i = 0; i < data.length; i++) {
					bytesWritten.add(data[i]);
				}
			}
			if(opts.contains(StandardOpenOption.CREATE_NEW)) {
				throw new FileAlreadyExistsException(new String(resolvedPath));
			}
		} else {
			if(!opts.contains(StandardOpenOption.CREATE) && !opts.contains(StandardOpenOption.CREATE_NEW)) {
				throw new FileNotFoundException(new String(resolvedPath));
			}
		}
		return new OutputStream() {
			
			@Override
			public void write(int b) throws IOException {
				bytesWritten.add((byte) b);
			}
			
			@Override
			public void close() throws IOException {
				byte[] data = new byte[TarConstants.HEADER_BLOCK+bytesWritten.size()];
				TarEntry e = new TarEntry(TarHeader.createHeader(new String(resolvedPath), bytesWritten.size(), System.currentTimeMillis(), false));
				e.writeEntryHeader(data);
				for(int i = 0; i < bytesWritten.size(); i++) {
					data[TarConstants.HEADER_BLOCK+i] = bytesWritten.get(i);
				}
				data = Arrays.copyOf(data, (int) Math.ceil((double) data.length / TarConstants.DATA_BLOCK)*TarConstants.DATA_BLOCK);
				if(exists(resolvedPath)) {
					TarEntry te = getTarEntryFromPath(resolvedPath);
					int dataBlocks = (int) Math.ceil((double) te.getSize() / TarConstants.DATA_BLOCK);
					int overAllLength = (dataBlocks+1)*TarConstants.DATA_BLOCK;
					truncateEntry(entryHeadersOffsets.get(te.getName()), overAllLength);
				}
				addEntryToByteArray(data);
			}
		};
	}
	
	private byte[] getDataBytes(byte[] path) {
		TarEntry te = getTarEntryFromPath(path);
		int offset = entryHeadersOffsets.get(new String(path));
		byte[] data = Arrays.copyOfRange(tfByteArray, offset+TarConstants.HEADER_BLOCK, (int) (offset+TarConstants.HEADER_BLOCK+te.getSize()));
		return data;
	}

	public void copyFile(boolean b, byte[] resolvedPath, byte[] resolvedPath2,
			CopyOption... options) {
		// TODO Auto-generated method stub

	}

}
