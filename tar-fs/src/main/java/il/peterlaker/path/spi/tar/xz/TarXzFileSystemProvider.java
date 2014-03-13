package il.peterlaker.path.spi.tar.xz;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import il.peterlaker.path.spi.tar.AbstractTarFileSystem;
import il.peterlaker.path.spi.tar.AbstractTarFileSystemProvider;

public class TarXzFileSystemProvider extends AbstractTarFileSystemProvider {

	@Override
	protected AbstractTarFileSystem newInstance(
			AbstractTarFileSystemProvider provider, Path path,
			Map<String, ?> env) throws IOException {
		return new TarXzFileSystem(provider, path, env);
	}

	@Override
	public String getScheme() {
		return "tar.xz";
	}

}
