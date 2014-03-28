package com.github.peterlaker.nio.file.tar.gz;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.github.peterlaker.nio.file.tar.AbstractTarFileSystem;
import com.github.peterlaker.nio.file.tar.AbstractTarFileSystemProvider;

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
