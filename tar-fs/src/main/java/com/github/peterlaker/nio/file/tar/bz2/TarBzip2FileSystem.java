package com.github.peterlaker.nio.file.tar.bz2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import com.github.peterlaker.nio.file.tar.AbstractTarFileSystem;
import com.github.peterlaker.nio.file.tar.AbstractTarFileSystemProvider;
import com.github.peterlaker.nio.file.tar.TarUtils;

class TarBzip2FileSystem extends AbstractTarFileSystem {

	protected TarBzip2FileSystem(AbstractTarFileSystemProvider provider,
			Path tfpath, Map<String, ?> env) throws IOException {
		super(provider, tfpath, env);
	}

	@Override
	protected byte[] readFile(Path path) throws IOException {
		return TarUtils.readAllBytes(new BZip2CompressorInputStream(Files
				.newInputStream(path, StandardOpenOption.READ)));
	}

	@Override
	protected void writeFile(byte[] tarBytes, Path path) throws IOException {
		try (BZip2CompressorOutputStream outputStream = new BZip2CompressorOutputStream(
				Files.newOutputStream(path,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE))) {
			outputStream.write(tarBytes, 0, tarBytes.length);
			outputStream.finish();
			outputStream.flush();
		}
	}

}
