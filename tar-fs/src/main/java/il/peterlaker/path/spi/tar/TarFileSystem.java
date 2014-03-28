package il.peterlaker.path.spi.tar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class TarFileSystem extends AbstractTarFileSystem {

	protected TarFileSystem(AbstractTarFileSystemProvider provider,
			Path tfpath, Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile(Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	@Override
	protected void writeFile(byte[] tarBytes, Path path) throws IOException {
		Files.write(path, tarBytes, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

}
