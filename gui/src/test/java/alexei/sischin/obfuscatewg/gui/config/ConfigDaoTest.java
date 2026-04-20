/*
 * Copyright 2026 Alexei Sischin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alexei.sischin.obfuscatewg.gui.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigDaoTest {

    static final String MARKER_STR = "MARKER_STR";

    ObjectMapper OM = new ObjectMapper();

    AppDirs appDirs = mock();
    MockedStatic<AppDirsFactory> _AppDirsFactory = mockStatic();

    @BeforeAll
    void beforeAll() {
        _AppDirsFactory.when(AppDirsFactory::getInstance).thenReturn(appDirs);
    }

    @AfterAll
    void afterAll() {
        _AppDirsFactory.close();
    }

    @SneakyThrows
    @Test
    void save_givenNoConfig_createsNewOne() {
        Path configDirPath = buildConfigDirPath();
        mockAppDirs(configDirPath);

        SerializableConfig inputConfig = buildNewConfig();
        ConfigDao.save(inputConfig);

        Path configPath = configDirPath.resolve(ConfigDao.CONFIG_FILE_NAME);
        String configContent = Files.readString(configPath);
        SerializableConfig savedConfig = OM.readValue(configContent, SerializableConfig.class);
        assertThat(savedConfig).isEqualTo(inputConfig);
    }

    @SneakyThrows
    @Test
    void save_givenExistingConfig_overwritesIt() {
        Path configDirPath = buildConfigDirPath();
        mockAppDirs(configDirPath);
        SerializableConfig existingConfig = buildExistingConfig();
        writeToFile(existingConfig, configDirPath);

        SerializableConfig inputConfig = buildNewConfig();
        ConfigDao.save(inputConfig);

        Path configPath = configDirPath.resolve(ConfigDao.CONFIG_FILE_NAME);
        String configContent = Files.readString(configPath);
        SerializableConfig savedConfig = OM.readValue(configContent, SerializableConfig.class);
        assertThat(savedConfig).isEqualTo(inputConfig);
    }

    @SneakyThrows
    @Test
    void save_givenLargeExistingConfig_overwritesItWithoutTrailingBytes() {
        Path configDirPath = buildConfigDirPath();
        mockAppDirs(configDirPath);
        SerializableConfig existingConfig = buildLargeExistingConfig();
        writeToFile(existingConfig, configDirPath);

        SerializableConfig inputConfig = buildNewConfig();
        ConfigDao.save(inputConfig);

        Path configPath = configDirPath.resolve(ConfigDao.CONFIG_FILE_NAME);
        String configContent = Files.readString(configPath);
        assertThat(configContent).doesNotContain(MARKER_STR);

        SerializableConfig savedConfig = OM.readValue(configContent, SerializableConfig.class);
        assertThat(savedConfig).isEqualTo(inputConfig);
    }

    @SneakyThrows
    @Test
    void load_givenNoConfig_returnsEmptyResult() {
        Path configDirPath = buildConfigDirPath();
        mockAppDirs(configDirPath);

        Optional<SerializableConfig> result = ConfigDao.load();
        assertThat(result).isEmpty();
    }

    @SneakyThrows
    @Test
    void load_givenExistingConfig_parsesIt() {
        Path configDirPath = buildConfigDirPath();
        mockAppDirs(configDirPath);
        SerializableConfig existingConfig = buildExistingConfig();
        writeToFile(existingConfig, configDirPath);

        Optional<SerializableConfig> result = ConfigDao.load();
        assertThat(result).contains(existingConfig);
    }

    @SneakyThrows
    private static Path buildConfigDirPath() {
        Path rootDirPath = Files.createTempDirectory("test");
        return rootDirPath.resolve("some", "config", "dir");
    }

    private void mockAppDirs(Path configDirPath) {
        String configDirPathString = configDirPath.toAbsolutePath().toString();
        when(appDirs.getUserConfigDir(anyString(), isNull(), anyString())).thenReturn(configDirPathString);
    }

    @SneakyThrows
    private void writeToFile(SerializableConfig config, Path configDirPath) {
        Files.createDirectories(configDirPath);
        Path configPath = configDirPath.resolve(ConfigDao.CONFIG_FILE_NAME);
        byte[] existingConfigContent = OM.writeValueAsBytes(config);
        Files.write(configPath, existingConfigContent);
    }

    private static SerializableConfig buildExistingConfig() {
        return new SerializableConfig(
                "OFF",
                "file:///home/user/bin/protocol.jar",
                "secretKey",
                "NOOP",
                "1000",
                "127.0.0.1",
                "51820",
                "192.168.0.2",
                "51820",
                "1000",
                "2",
                "8"
        );
    }

    private static SerializableConfig buildLargeExistingConfig() {
        return new SerializableConfig(
                "OFF",
                "file:///home/user/bin/protocol.jar",
                IntStream.range(0, 1000).mapToObj(_ -> MARKER_STR).collect(Collectors.joining("_")),
                "NOOP",
                "1000",
                "127.0.0.1",
                "51820",
                "192.168.0.2",
                "51820",
                "1000",
                "2",
                "8"
        );
    }

    private static SerializableConfig buildNewConfig() {
        return new SerializableConfig(
                "TRACE",
                "file:///home/user/bin/protocol-new.jar",
                "newSecretKey",
                "OBFUSCATOR",
                "1100",
                "0.0.0.0",
                "555",
                "192.168.0.3",
                "555",
                "2000",
                "4",
                "16"
        );
    }
}