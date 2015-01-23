package com.github.peterlaker.nio.file.tar;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;

public class TarFileSystemTests {

	private Path createPath = Paths.get("createPath.tar");
	private Path sampleFile = Paths.get("src/test/resources/sampleFile.txt");

	@Test
	public void testTarCreation() throws Exception {
		if (!Files.exists(createPath)) {
			Files.createFile(createPath);
		}
		try (FileSystem tfs = FileSystems.newFileSystem(new URI("tar:"
				+ createPath.toUri().toString()),
				Collections.<String, Object> emptyMap())) {
			Path newFile = tfs.getPath("hell/sampleFile.txt");
			Files.copy(sampleFile, newFile, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

	@After
	public void after() throws IOException {
		Files.deleteIfExists(createPath);
	}

}
