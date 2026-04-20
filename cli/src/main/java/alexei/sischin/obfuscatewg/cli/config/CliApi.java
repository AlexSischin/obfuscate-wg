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

package alexei.sischin.obfuscatewg.cli.config;

import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.help.TextHelpAppendable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Command line interface and application argument configuration support.
 */
public final class CliApi implements ConfigSource {

    private static final int HELP_MAX_WIDTH = 128;
    private static final CommandLineParser PARSER = new DefaultParser();

    private final Options options;

    @Nullable
    private CommandLine commandLine;

    private final AtomicBoolean started = new AtomicBoolean();

    public CliApi() {
        this.options = buildOptions();
    }

    public synchronized void initialize(String[] args) throws ParseException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Already initialized");
        }
        this.commandLine = PARSER.parse(options, args);
    }

    public boolean isHelp() {
        return this.commandLine.hasOption("h");
    }

    @Override
    public Optional<String> get(ConfigProperty propKey) {
        if (this.commandLine == null) {
            throw new IllegalStateException("Not initialized. Invoke \"initialize\" first.");
        }
        Arg arg = Arg.forKey(propKey);
        return Optional.ofNullable(this.commandLine.getOptionValue(arg.name));
    }

    public void printHelp() throws IOException {
        TextHelpAppendable helpAppendable = new TextHelpAppendable(System.out);
        helpAppendable.setMaxWidth(HELP_MAX_WIDTH);
        HelpFormatter helpFormatter = HelpFormatter.builder().setHelpAppendable(helpAppendable).get();

        String cmdLineSyntax = "java -jar <JAR_FILE>";
        String header = "Run a WireGuard proxy that obfuscates protocol fingerprint. It can be run in either obfuscation or deobfuscation mode.";
        String defaultArgValues = Arrays.stream(ConfigProperty.values())
                .filter(propKey -> propKey.getDefaultValue().isPresent())
                .map(propKey -> "- %s = %s".formatted(propKey, propKey.getDefaultValue().get()))
                .collect(Collectors.joining("\n"));
        String requiredArgs = Arrays.stream(ConfigProperty.values())
                .filter(propKey -> propKey.getDefaultValue().isEmpty())
                .map("- %s"::formatted)
                .collect(Collectors.joining("\n"));
        String footer = """
                Parameters could be also specified via JVM params or environment variables.
                Defaults:
                %s
                Required params (could be specified either way):
                %s""".formatted(defaultArgValues, requiredArgs);
        helpFormatter.printHelp(cmdLineSyntax, header, options, footer, true);
    }

    private static Options buildOptions() {
        Options options = new Options();
        Arrays.stream(Arg.values())
                .map(Arg::toOption)
                .forEach(options::addOption);
        options.addOption("h", "help", false, "Display help information.");
        return options;
    }

    @Getter
    @RequiredArgsConstructor
    private enum Arg {
        LOG_LEVEL(ConfigProperty.LOG_LEVEL, "l", "log-level"),
        PROTOCOL_URL(ConfigProperty.PROTOCOL_URL, "u", "protocol-url"),
        PROTOCOL_ARGS(ConfigProperty.PROTOCOL_ARGS, "a", "protocol-args"),
        MODE(ConfigProperty.MODE, "m", "mode"),
        WG_MTU(ConfigProperty.WG_MTU, "M", "wg-mtu"),
        IP(ConfigProperty.IP, "i", "ip"),
        PORT(ConfigProperty.PORT, "p", "port"),
        PEER_IP(ConfigProperty.PEER_IP, "I", "peer-ip"),
        PEER_PORT(ConfigProperty.PEER_PORT, "P", "peer-port"),
        QUEUE_SIZE(ConfigProperty.QUEUE_SIZE, "q", "queue-size"),
        QUEUE_PROCESSORS(ConfigProperty.QUEUE_PROCESSORS, "Q", "queue-processors"),
        MAX_CLIENT_SESSIONS(ConfigProperty.MAX_SESSIONS, "s", "max-sessions");

        private final ConfigProperty propKey;
        private final String name;
        private final String longName;

        private static Arg forKey(ConfigProperty propKey) {
            return Arrays.stream(values())
                    .filter(v -> v.propKey.equals(propKey))
                    .findAny().orElseThrow(() -> new UnsupportedOperationException(
                            "No arg exists for key %s".formatted(propKey)
                    ));
        }

        private Option toOption() {
            StringBuilder descriptionBuilder = new StringBuilder(propKey.getDescription());
            propKey.getPossibleValues().ifPresent(values -> {
                descriptionBuilder.append(" Possible values: ");
                descriptionBuilder.append(values.stream().collect(Collectors.joining(", ")));
            });
            return Option.builder(name)
                    .longOpt(longName)
                    .hasArg()
                    .argName(propKey.toString())
                    .desc(descriptionBuilder.toString())
                    .required(false)
                    .get();
        }
    }
}
