package il.peterlaker.path.spi.tar.gz;

import il.peterlaker.path.spi.tar.TarConstants;
import il.peterlaker.path.spi.tar.AbstractTarFileSystem;
import il.peterlaker.path.spi.tar.AbstractTarFileSystemProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TarGzipFileSystem extends AbstractTarFileSystem {

	TarGzipFileSystem(AbstractTarFileSystemProvider provider, Path tfpath,
			Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new byte[TarConstants.DATA_BLOCK];
		}
		GZIPInputStream gzipInputStream = new GZIPInputStream(
				Files.newInputStream(path, StandardOpenOption.READ));
		ArrayList<Byte> ret = new ArrayList<>();
		while (gzipInputStream.available() > 0) {
			ret.add((byte) gzipInputStream.read());
		}
		gzipInputStream.close();
		byte[] inflatedBytes = new byte[ret.size()];
		for (int i = 0; i < inflatedBytes.length; i++) {
			inflatedBytes[i] = ret.get(i);
		}
		return inflatedBytes;
	}

	@Override
	protected void writeFile(byte[] tarBytes, Path path) throws IOException {
		GZIPOutputStream os = new GZIPOutputStream(Files.newOutputStream(path,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
		os.write(tarBytes, 0, tarBytes.length);
		os.flush();
		os.finish();
		os.close();
	}

}
