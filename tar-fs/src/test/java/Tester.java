import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class Tester {

	@Test
	public void test() throws IOException {
		Map<String, Object> env = new HashMap<>();
		env.put("create", "true");
		try (FileSystem fs = FileSystems.newFileSystem(new URI(
				"tar:file:///C:/Users/Stav/temp.tar"), env)) {
			Path path = fs.getPath("someDir2/");
			Files.createDirectory(path);
			Assert.assertEquals(true, Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes().isDirectory());
			Path file = fs.getPath("someDir2/a.txt");
			Files.write(file, "SDFSDFSDFSDFSDFSDFSDFFDSF".getBytes(), StandardOpenOption.CREATE_NEW);
			Assert.assertEquals(false, Files.getFileAttributeView(file, BasicFileAttributeView.class).readAttributes().isDirectory());
			Path copy = fs.getPath("copy/copy.txt");
			Files.copy(file, copy);
			Assert.assertEquals(true, Files.exists(copy));
			byte[] fileBytes = Files.readAllBytes(file);
			byte[] copyBytes = Files.readAllBytes(copy);
			Assert.assertArrayEquals(fileBytes, copyBytes);
		} catch (IOException | URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Path tarPath = Paths.get("C:/Users/Stav/temp.tar");
		Files.delete(tarPath);
	}

}
