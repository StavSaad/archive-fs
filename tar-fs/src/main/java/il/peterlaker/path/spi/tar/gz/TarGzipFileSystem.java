package il.peterlaker.path.spi.tar.gz;

import il.peterlaker.path.spi.tar.TarConstants;
import il.peterlaker.path.spi.tar.TarFileSystem;
import il.peterlaker.path.spi.tar.TarFileSystemProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TarGzipFileSystem extends TarFileSystem {

	TarGzipFileSystem(TarFileSystemProvider provider, Path tfpath,
			Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile() throws IOException {
		if(!Files.exists(tfpath)) {
			return new byte[TarConstants.DATA_BLOCK];
		}
		GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(tfpath, StandardOpenOption.READ));
		ArrayList<Byte> ret = new ArrayList<>();
		while (gzipInputStream.available() > 0) {
			ret.add((byte) gzipInputStream.read());
		}
		byte[] inflatedBytes = new byte[ret.size()];
		for (int i = 0; i < inflatedBytes.length; i++) {
			inflatedBytes[i] = ret.get(i);
		}
		return inflatedBytes;
	}

	@Override
	protected void writeFile(byte[] tarBytes) throws IOException {
		GZIPOutputStream os = new GZIPOutputStream(Files.newOutputStream(
				tfpath, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE));
		os.write(tarBytes, 0, tarBytes.length);
		os.flush();
		os.finish();
		os.close();
	}

}
