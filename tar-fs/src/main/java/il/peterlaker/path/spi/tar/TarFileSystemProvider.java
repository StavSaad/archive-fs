package il.peterlaker.path.spi.tar;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class TarFileSystemProvider extends AbstractTarFileSystemProvider {

	@Override
	protected AbstractTarFileSystem newInstance(
			AbstractTarFileSystemProvider provider, Path path,
			Map<String, ?> env) throws IOException {
		return new TarFileSystem(provider, path, env);
	}

	@Override
	public String getScheme() {
		return "tar";
	}

}
