package hu.blackbelt.judo.psm.generator.engine;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Disabled
public class GeneratorIgnoreTest {
    static final String TMP_DIR_PREFIX = "generatorIgnoreTestTarget";
    Path tmpTargetDir;
    Path ignoreFilePath;
    GeneratorIgnore generatorIgnore;

    @BeforeEach
    public void setUp() throws Exception {
        tmpTargetDir = Files.createTempDirectory(Paths.get("target"), TMP_DIR_PREFIX);
        ignoreFilePath = Paths.get(tmpTargetDir.toString(), GeneratorIgnore.GENERATOR_IGNORE_FILE);
        String newLine = System.getProperty("line.separator");
        String content = String.join(newLine, "**/*.php", "app.yaml", "folder-contents-to-ignore/**", "test/*/testing.txt");
        Files.write(ignoreFilePath, content.getBytes(StandardCharsets.UTF_8));
        generatorIgnore = new GeneratorIgnore(tmpTargetDir);
    }

    @Test
    void testTargetTempFolderCreation() {
        assertThat(tmpTargetDir.toFile().getPath(), startsWith("target"));
    }

    @Test
    void testIgnoreFileCreation() {
        File fileWithAbsolutePath = ignoreFilePath.toFile();

        assertTrue(fileWithAbsolutePath.exists());
    }

    @Test
    void testReadGlobs() {
        assertEquals(Arrays.asList("**/*.php", "app.yaml", "folder-contents-to-ignore/**", "test/*/testing.txt"), generatorIgnore.getGlobs());
    }

    @Test
    void testShouldExcludeExplicitFileInRoot() {
        Path path1 = absolutePathFor("app.yaml");
        Path path2 = absolutePathFor("lol.yaml");

        assertTrue(generatorIgnore.shouldExcludeFile(path1));
        assertFalse(generatorIgnore.shouldExcludeFile(path2));
    }

    @Test
    void testShouldExcludeFileInAnyLevel() {
        Path path1 = absolutePathFor("first", "second", "third", "theFile.php");
        Path path2 = absolutePathFor("first", "second", "third", "theFile.pdf");

        assertTrue(generatorIgnore.shouldExcludeFile(path1));
        assertFalse(generatorIgnore.shouldExcludeFile(path2));
    }

    @Test
    void testShouldExcludeFilesOneLevelDeep() {
        Path path1 = absolutePathFor("test", "one", "testing.txt");
        Path path2 = absolutePathFor("test", "two", "testing.txt");
        Path path3 = absolutePathFor("test", "two", "two-two", "testing.txt");

        assertTrue(generatorIgnore.shouldExcludeFile(path1));
        assertTrue(generatorIgnore.shouldExcludeFile(path2));
        assertFalse(generatorIgnore.shouldExcludeFile(path3));
    }

    @Test
    void testShouldExcludeAllInFolder() {
        Path path1 = absolutePathFor("folder-contents-to-ignore", "one", "testing.txt");
        Path path2 = absolutePathFor("folder-contents-to-ignore", "testing.txt");
        Path path3 = absolutePathFor("folder-contents-to-ignore");
        Path path4 = absolutePathFor("test", "testing.txt");

        assertTrue(generatorIgnore.shouldExcludeFile(path1));
        assertTrue(generatorIgnore.shouldExcludeFile(path2));
        assertFalse(generatorIgnore.shouldExcludeFile(path3)); // only contents!
        assertFalse(generatorIgnore.shouldExcludeFile(path4));
    }

    Path absolutePathFor(String... relativePath) {
        return Paths.get(tmpTargetDir.toString(), relativePath);
    }
}
