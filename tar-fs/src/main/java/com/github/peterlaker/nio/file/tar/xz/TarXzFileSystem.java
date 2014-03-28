package com.github.peterlaker.nio.file.tar.xz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import com.github.peterlaker.nio.file.tar.AbstractTarFileSystem;
import com.github.peterlaker.nio.file.tar.AbstractTarFileSystemProvider;
import com.github.peterlaker.nio.file.tar.TarUtils;

class TarXzFileSystem extends AbstractTarFileSystem {

	protected TarXzFileSystem(AbstractTarFileSystemProvider provider,
			Path tfpath, Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile(Path path) throws IOException {
		return TarUtils.readAllBytes(new XZCompressorInputStream(Files
				.newInputStream(path, StandardOpenOption.READ)));
	}

	@Override
	protected void writeFile(byte[] tarBytes, Path path) throws IOException {
		XZCompressorOutputStream outputStream = new XZCompressorOutputStream(
				Files.newOutputStream(path,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE));
		outputStream.write(tarBytes, 0, tarBytes.length);
		outputStream.finish();
		outputStream.flush();
		outputStream.close();
	}

}
