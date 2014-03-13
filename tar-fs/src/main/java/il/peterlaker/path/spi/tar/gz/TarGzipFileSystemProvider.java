package il.peterlaker.path.spi.tar.gz;

import il.peterlaker.path.spi.tar.AbstractTarFileSystem;
import il.peterlaker.path.spi.tar.AbstractTarFileSystemProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class TarGzipFileSystemProvider extends AbstractTarFileSystemProvider {

	@Override
	public String getScheme() {
		return "tar.gz";
	}

	@Override
	protected AbstractTarFileSystem newInstance(
			AbstractTarFileSystemProvider provider, Path path,
			Map<String, ?> env) throws IOException {
		return new TarGzipFileSystem(provider, path, env);
	}
	
}
