package il.peterlaker.path.spi.tar.xz;

import il.peterlaker.path.spi.tar.AbstractTarFileSystem;
import il.peterlaker.path.spi.tar.AbstractTarFileSystemProvider;
import il.peterlaker.path.spi.tar.TarConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

public class TarXzFileSystem extends AbstractTarFileSystem {

	protected TarXzFileSystem(AbstractTarFileSystemProvider provider,
			Path tfpath, Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile() throws IOException {
		if(!Files.exists(tfpath)) {
			return new byte[TarConstants.DATA_BLOCK];
		}
		XZCompressorInputStream inputStream = new XZCompressorInputStream(
				Files.newInputStream(tfpath));
		List<Byte> bytes = new ArrayList<>();
		while (inputStream.available() > 0) {
			bytes.add((byte) inputStream.read());
		}
		inputStream.close();
		byte[] ret = new byte[bytes.size()];
		for (int i = 0; i < bytes.size(); i++) {
			ret[i] = bytes.get(i);
		}
		return ret;
	}

	@Override
	protected void writeFile(byte[] tarBytes) throws IOException {
		XZCompressorOutputStream outputStream = new XZCompressorOutputStream(
				Files.newOutputStream(tfpath,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE));
		outputStream.write(tarBytes, 0, tarBytes.length);
		outputStream.finish();
		outputStream.flush();
		outputStream.close();
	}

}