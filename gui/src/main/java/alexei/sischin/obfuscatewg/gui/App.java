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

package alexei.sischin.obfuscatewg.gui;

import alexei.sischin.obfuscatewg.core.datagram.DatagramBridge;
import alexei.sischin.obfuscatewg.core.util.protocol.ProtocolLoader;
import alexei.sischin.obfuscatewg.core.util.bridge.DatagramBridgeBuilder;
import alexei.sischin.obfuscatewg.core.util.logging.LogbackConfig;
import alexei.sischin.obfuscatewg.core.util.config.ConfigProperty;
import alexei.sischin.obfuscatewg.core.util.bridge.BridgeConfig;
import alexei.sischin.obfuscatewg.core.util.logging.LogConfig;
import alexei.sischin.obfuscatewg.gui.config.ConfigDao;
import alexei.sischin.obfuscatewg.gui.config.SerializableConfig;
import alexei.sischin.obfuscatewg.gui.logging.TextAreaAppender;
import alexei.sischin.obfuscatewg.protocol.api.Protocol;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.function.Predicate.not;

@Slf4j
public class App extends Application {

    private final BackendThreadFactory backendThreadFactory = new BackendThreadFactory();
    private final ExecutorService backendExecutor = Executors.newSingleThreadExecutor(backendThreadFactory);
    private final Thread shutdownHook = new ShutdownHook(backendExecutor);

    private boolean running = false;
    private ProtocolLoader protocolLoader;

    private VBox configTabContent;
    private TextField protocolUrlField;
    private Button browseButton;
    private Button saveSettingsButton;
    private Button importSettingsButton;

    private CheckBox advancedSettingsCheckBox;
    private VBox advancedSettingsContainer;

    private PasswordField protocolArgsField;
    private ComboBox<String> logLevelBox;
    private ComboBox<String> modeBox;
    private TextField ipField;
    private Spinner<Integer> portSpinner;
    private TextField peerIpField;
    private Spinner<Integer> peerPortSpinner;
    private Spinner<Integer> wgMtuSpinner;
    private Spinner<Integer> queueSizeSpinner;
    private Spinner<Integer> queueProcessorsSpinner;
    private Spinner<Integer> maxSessionsSpinner;

    private Button reloadSettingsButton;
    private Button resetToDefaultsButton;
    private Button startStopButton;

    private TextArea logArea;

    public static void main(String[] args) {
        LogbackConfig.init();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        TabPane tabPane = createTabPane(stage);
        VBox footerPane = createFooterPane();
        VBox root = new VBox(tabPane, footerPane);
        root.setPrefWidth(800);
        Scene scene = new Scene(root);
        stage.setTitle("ObfuscateWG");
        stage.setScene(scene);
        if (!loadSettings()) {
            setDefaults();
        }
        stage.getIcons().add(new Image(
                Objects.requireNonNull(getClass().getResourceAsStream("/icon/icon_small.png"))
        ));
        stage.show();
    }

    @Override
    public void stop() {
        shutdownHook.start();
        closeProtocolLoader();
    }

    private TabPane createTabPane(Stage stage) {
        Tab configTab = createConfigTab(stage);
        Tab logTab = createLogTab();
        TabPane pane = new TabPane(configTab, logTab);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private Tab createConfigTab(Stage stage) {
        GridPane configGrid = createConfigGrid(stage);
        GridPane buttonGrid = createButtonGrid(stage);
        GridPane additionalConfigGrid = createAdditionalConfigGrid();
        GridPane additionalButtonGrid = createAdditionalButtonGrid();

        advancedSettingsCheckBox = new CheckBox("Advanced settings");
        advancedSettingsCheckBox.setSelected(false);

        advancedSettingsContainer = new VBox(10, additionalConfigGrid, additionalButtonGrid);
        advancedSettingsContainer.disableProperty().bind(
                advancedSettingsCheckBox.selectedProperty().not()
        );

        configTabContent = new VBox(
                10,
                configGrid,
                buttonGrid,
                advancedSettingsCheckBox,
                advancedSettingsContainer
        );
        configTabContent.setPadding(new Insets(12));

        Tab tab = new Tab("Configuration", configTabContent);
        tab.setClosable(false);
        return tab;
    }

    private GridPane createConfigGrid(Stage stage) {
        GridPane grid = new GridPane(16, 8);

        int row = 0;

        protocolUrlField = new TextField();
        browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Protocol JAR");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JAR Files", "*.jar")
            );

            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                protocolUrlField.setText(selected.toURI().toString());
            }
        });
        HBox protocolBox = new HBox(8, protocolUrlField, browseButton);
        HBox.setHgrow(protocolUrlField, Priority.ALWAYS);
        addField(grid, row++, 0, "Protocol JAR", protocolBox, 2);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(50);
        columnConstraints.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(
                columnConstraints,
                columnConstraints
        );
        return grid;
    }

    private GridPane createAdditionalConfigGrid() {
        GridPane grid = new GridPane(16, 8);

        int row = 0;

        protocolArgsField = new PasswordField();
        addField(grid, row++, 0, "Protocol Args", protocolArgsField, 2);

        logLevelBox = new ComboBox<>();
        logLevelBox.getItems().addAll(ConfigProperty.LOG_LEVEL.getPossibleValues().orElseThrow());
        addField(grid, row, 0, "Log Level", logLevelBox);
        logLevelBox.getSelectionModel().selectedItemProperty()
                .addListener((_, _, newValue) -> configureLogging());

        modeBox = new ComboBox<>();
        modeBox.getItems().addAll(ConfigProperty.MODE.getPossibleValues().orElseThrow());
        addField(grid, row++, 1, "Mode", modeBox);

        ipField = new TextField();
        addField(grid, row, 0, "Listen IP", ipField);

        portSpinner = new Spinner<>(1, 0xFFFF, 1);
        portSpinner.setEditable(true);
        addField(grid, row++, 1, "Listen port", portSpinner);

        peerIpField = new TextField();
        peerIpField.setEditable(true);
        addField(grid, row, 0, "Peer IP", peerIpField);

        peerPortSpinner = new Spinner<>(1, 0xFFFF, 1);
        peerPortSpinner.setEditable(true);
        addField(grid, row++, 1, "Peer port", peerPortSpinner);

        wgMtuSpinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
        wgMtuSpinner.setEditable(true);
        addField(grid, row, 0, "WG MTU", wgMtuSpinner);

        queueSizeSpinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
        queueSizeSpinner.setEditable(true);
        addField(grid, row++, 1, "Queue Size", queueSizeSpinner);

        queueProcessorsSpinner = new Spinner<>(0, Integer.MAX_VALUE, 1);
        queueProcessorsSpinner.setEditable(true);
        addField(grid, row, 0, "Queue Processors", queueProcessorsSpinner);

        maxSessionsSpinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
        maxSessionsSpinner.setEditable(true);
        addField(grid, row++, 1, "Max Sessions", maxSessionsSpinner);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(50);
        columnConstraints.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(
                columnConstraints,
                columnConstraints
        );
        return grid;
    }

    private void addField(GridPane grid, int row, int column, String labelText, Region region) {
        addField(grid, row, column, labelText, region, 1);
    }

    private void addField(GridPane grid, int row, int column, String labelText, Region region, int colSpan) {
        Label label = new Label(labelText + ":");
        label.setPrefWidth(120);
        HBox hBox = new HBox(8, label, region);
        hBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(region, Priority.ALWAYS);
        region.setMaxWidth(Double.MAX_VALUE);
        grid.add(hBox, column, row, colSpan, 1);
    }

    private GridPane createButtonGrid(Stage stage) {
        GridPane grid = new GridPane(16, 8);

        int row = 0;

        saveSettingsButton = new Button("Save settings");
        saveSettingsButton.setMaxWidth(Double.MAX_VALUE);
        saveSettingsButton.setOnAction(_ -> saveSettings());
        addButton(grid, row, 0, saveSettingsButton);

        importSettingsButton = new Button("Import settings");
        importSettingsButton.setMaxWidth(Double.MAX_VALUE);
        importSettingsButton.setOnAction(_ -> importSettings(stage));
        addButton(grid, row, 1, importSettingsButton);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth((double) 100 / 2);
        columnConstraints.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(
                columnConstraints,
                columnConstraints
        );

        return grid;
    }

    private GridPane createAdditionalButtonGrid() {
        GridPane grid = new GridPane(16, 8);

        int row = 0;

        reloadSettingsButton = new Button("Reload settings");
        reloadSettingsButton.setMaxWidth(Double.MAX_VALUE);
        reloadSettingsButton.setOnAction(_ -> loadSettings());
        addButton(grid, ++row, 0, reloadSettingsButton);

        resetToDefaultsButton = new Button("Reset to defaults");
        resetToDefaultsButton.setMaxWidth(Double.MAX_VALUE);
        resetToDefaultsButton.setOnAction(_ -> setDefaults());
        addButton(grid, row, 1, resetToDefaultsButton);

        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth((double) 100 / 2);
        columnConstraints.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(
                columnConstraints,
                columnConstraints
        );

        return grid;
    }

    private void addButton(GridPane grid, int row, int column, Region region) {
        HBox.setHgrow(region, Priority.ALWAYS);
        region.setMaxWidth(Double.MAX_VALUE);
        grid.add(region, column, row);
    }

    private Tab createLogTab() {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefRowCount(1);
        VBox logsTabContent = new VBox(10, logArea);
        logsTabContent.setPadding(new Insets(12));
        VBox.setVgrow(logArea, Priority.ALWAYS);
        LogbackConfig.init(new TextAreaAppender(logArea));
        Tab tab = new Tab("Logs", logsTabContent);
        tab.setClosable(false);
        return tab;
    }

    private VBox createFooterPane() {
        startStopButton = new Button("Start");
        startStopButton.setMaxWidth(Double.MAX_VALUE);
        startStopButton.setPrefHeight(50);
        startStopButton.setOnAction(_ -> toggleRunning());
        startStopButton.setStyle("-fx-base: #AAFFAA;");

        VBox pane = new VBox(10, startStopButton);
        pane.setPadding(new Insets(0, 12, 12, 12));
        return pane;
    }

    private void saveSettings() {
        try {
            SerializableConfig config = getGUISettings();
            ConfigDao.save(config);
            log.info("Settings are saved");
        } catch (Exception e) {
            log.error("Failed to save config", e);
        }
    }

    private void importSettings(Stage stage) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select JSON config");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );

            File file = chooser.showOpenDialog(stage);
            if (file == null) {
                return;
            }

            SerializableConfig config = ConfigDao.load(file.toPath()).orElse(null);
            if (config != null) {
                setGUISettings(config);
                log.info("Configuration imported");
            } else {
                log.warn("Selected file does not contain a configuration");
            }
        } catch (Exception e) {
            log.error("Failed to import configuration", e);
        }
    }

    private boolean loadSettings() {
        try {
            SerializableConfig config = ConfigDao.load().orElse(null);
            if (config != null) {
                setGUISettings(config);
                return true;
            } else {
                log.warn("Settings do not exist");
            }
        } catch (Exception e) {
            log.error("Failed to load config", e);
        }
        return false;
    }

    private void setDefaults() {
        SerializableConfig config = new SerializableConfig(
                ConfigProperty.LOG_LEVEL.getDefaultValue().orElse(null),
                ConfigProperty.PROTOCOL_URL.getDefaultValue().orElse(null),
                ConfigProperty.PROTOCOL_ARGS.getDefaultValue().orElse(null),
                ConfigProperty.MODE.getDefaultValue().orElse(null),
                ConfigProperty.WG_MTU.getDefaultValue().orElse(null),
                ConfigProperty.IP.getDefaultValue().orElse(null),
                ConfigProperty.PORT.getDefaultValue().orElse(null),
                ConfigProperty.PEER_IP.getDefaultValue().orElse(null),
                ConfigProperty.PEER_PORT.getDefaultValue().orElse(null),
                ConfigProperty.QUEUE_SIZE.getDefaultValue().orElse(null),
                ConfigProperty.QUEUE_PROCESSORS.getDefaultValue().orElse(null),
                ConfigProperty.MAX_SESSIONS.getDefaultValue().orElse(null)
        );
        setGUISettings(config);
    }

    private void setGUISettings(SerializableConfig config) {
        logLevelBox.setValue(config.logLevel());
        protocolUrlField.setText(config.protocolUrl());
        protocolArgsField.setText(config.protocolArgs());
        modeBox.setValue(config.mode());
        wgMtuSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.wgMTU()).map(Integer::parseInt).orElse(null));
        ipField.setText(config.ip());
        portSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.port()).map(Integer::parseInt).orElse(null));
        peerIpField.setText(config.peerIp());
        peerPortSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.peerPort()).map(Integer::parseInt).orElse(null));
        queueSizeSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.queueSize()).map(Integer::parseInt).orElse(null));
        queueProcessorsSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.queueProcessors()).map(Integer::parseInt).orElse(null));
        maxSessionsSpinner.getValueFactory().setValue(
                Optional.ofNullable(config.maxSessions()).map(Integer::parseInt).orElse(null));
        log.debug("Updated config values");
    }

    private SerializableConfig getGUISettings() {
        return new SerializableConfig(
                Optional.ofNullable(logLevelBox.getValue()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(protocolUrlField.getText()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(protocolArgsField.getText()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(modeBox.getValue()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(wgMtuSpinner.getValue()).map(String::valueOf).orElse(null),
                Optional.ofNullable(ipField.getText()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(portSpinner.getValue()).map(String::valueOf).orElse(null),
                Optional.ofNullable(peerIpField.getText()).filter(not(String::isBlank)).orElse(null),
                Optional.ofNullable(peerPortSpinner.getValue()).map(String::valueOf).orElse(null),
                Optional.ofNullable(queueSizeSpinner.getValue()).map(String::valueOf).orElse(null),
                Optional.ofNullable(queueProcessorsSpinner.getValue()).map(String::valueOf).orElse(null),
                Optional.ofNullable(maxSessionsSpinner.getValue()).map(String::valueOf).orElse(null)
        );
    }

    private void configureLogging() {
        SerializableConfig guiSettings = getGUISettings();
        LogConfig logConfig = guiSettings.toLogConfig();
        LogbackConfig.configure(logConfig, new TextAreaAppender(logArea));
    }

    private void toggleRunning() {
        if (running) {
            log.debug("Stopping");
            stopBackend();

            startStopButton.setText("Start");
            startStopButton.setStyle("-fx-base: #AAFFAA;");
            configTabContent.setDisable(false);
        } else {
            log.debug("Starting");
            if (!startBackend()) {
                return;
            }

            startStopButton.setText("Stop");
            startStopButton.setStyle("-fx-base: #FFAAAA;");
            configTabContent.setDisable(true);
        }
        running = !running;
    }

    private void stopBackend() {
        backendThreadFactory.interruptCurrentThread();
    }

    private boolean startBackend() {
        AtomicBoolean started = new AtomicBoolean();
        buildConfig().ifPresent(config ->
                buildProtocolLoader(config)
                        .flatMap(protocolLoader -> loadProtocol(protocolLoader, config))
                        .ifPresent(protocol -> {
                            buildDatagramBridge(config, protocol).ifPresent(backendExecutor::submit);
                            started.set(true);
                        }));
        return started.get();
    }

    private Optional<BridgeConfig> buildConfig() {
        try {
            SerializableConfig guiSettings = getGUISettings();
            return Optional.of(guiSettings.toConfig());
        } catch (Exception e) {
            log.error("Configuration error", e);
            return Optional.empty();
        }
    }

    private Optional<ProtocolLoader> buildProtocolLoader(BridgeConfig config) {
        try {
            closeProtocolLoader();
            protocolLoader = new ProtocolLoader(config.protocolUrl());
            log.trace("Created protocol loader");
            return Optional.of(protocolLoader);
        } catch (Exception e) {
            log.error("Failed to build protocol loader", e);
            return Optional.empty();
        }
    }

    private void closeProtocolLoader() {
        if (protocolLoader != null) {
            try {
                log.trace("Closing protocol loader");
                protocolLoader.close();
            } catch (Exception e) {
                log.error("Failed to close protocol loader", e);
            }
        }
    }

    private static Optional<Protocol> loadProtocol(ProtocolLoader protocolLoader, BridgeConfig bridgeConfig) {
        try {
            String args = bridgeConfig.protocolArgs().orElse(null);
            Protocol protocol = protocolLoader.load(args);
            log.info("Loaded protocol: {}", protocol.getClass());
            return Optional.of(protocol);
        } catch (Exception e) {
            log.error("Failed to load protocol", e);
            return Optional.empty();
        }
    }

    private static Optional<DatagramBridge> buildDatagramBridge(BridgeConfig config, Protocol protocol) {
        try {
            return Optional.of(DatagramBridgeBuilder.build(config, protocol));
        } catch (Exception e) {
            log.error("Failed to build datagram bridge", e);
            return Optional.empty();
        }
    }
}