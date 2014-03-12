package il.peterlaker.path.spi.tar.gz;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

import il.peterlaker.path.spi.tar.TarFileSystem;
import il.peterlaker.path.spi.tar.TarFileSystemProvider;

public class TarGzipFileSystemProvider extends TarFileSystemProvider {

	@Override
	public String getScheme() {
		return "tar.gz";
	}
	
	@Override
	public FileSystem newFileSystem(Path path, Map<String, ?> env)
			throws IOException {
		if (path.getFileSystem() != FileSystems.getDefault()) {
			throw new UnsupportedOperationException();
		}
		ensureFile(path);
		return new TarGzipFileSystem(this, path, env);
	}
	
	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env)
			throws IOException {
		Path path = uriToPath(uri);
		synchronized (filesystems) {
			Path realPath = null;
			if (ensureFile(path)) {
				realPath = path.toRealPath();
				if (filesystems.containsKey(realPath))
					throw new FileSystemAlreadyExistsException();
			}
			TarFileSystem tarfs = null;
			tarfs = new TarGzipFileSystem(this, path, env);
			filesystems.put(realPath, tarfs);
			return tarfs;
		}
	}
	
}
