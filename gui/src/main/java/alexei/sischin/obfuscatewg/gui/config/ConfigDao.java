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
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

@Slf4j
@UtilityClass
public class ConfigDao {

    public static final String APP_NAME = "obfuscatewg";
    public static final String APP_AUTHOR = "alexei-sischin";
    public static final String CONFIG_FILE_NAME = "conf.json";

    private static final ObjectMapper OM = new ObjectMapper();
    private static final AppDirs APP_DIRS = AppDirsFactory.getInstance();

    public static void save(SerializableConfig config) throws IOException {
        Path dirPath = getDir();

        Files.createDirectories(dirPath);

        Path filePath = dirPath.resolve(CONFIG_FILE_NAME);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.trace("Deleted existing config file");
        }
        Files.createFile(filePath);
        log.trace("Created new blank config file");

        byte[] bytes = OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(config);
        Files.write(filePath, bytes, StandardOpenOption.WRITE);
        log.debug("Wrote config at \"{}\"", filePath);
    }

    public static Optional<SerializableConfig> load() throws IOException {
        Path dirPath = getDir();
        Path filePath = dirPath.resolve(CONFIG_FILE_NAME);
        return load(filePath);
    }

    public static Optional<SerializableConfig> load(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            log.debug("Config file does not exist at {}", filePath);
            return Optional.empty();
        }

        byte[] bytes = Files.readAllBytes(filePath);
        SerializableConfig config = OM.readValue(bytes, SerializableConfig.class);
        return Optional.of(config);
    }

    private static Path getDir() {
        String dirPathStr = APP_DIRS.getUserConfigDir(APP_NAME, null, APP_AUTHOR);
        return Path.of(dirPathStr);
    }
}
