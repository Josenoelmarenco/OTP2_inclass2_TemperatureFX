package fi.metropolia.tempfx.ui;

import fi.metropolia.tempfx.SpeedCalculator;
import fi.metropolia.tempfx.TemperatureConverter;
import fi.metropolia.tempfx.db.TripRecordDao;
import fi.metropolia.tempfx.db.UnitSystemDao;
import fi.metropolia.tempfx.i18n.LanguageOption;
import fi.metropolia.tempfx.i18n.LocalizationService;
import fi.metropolia.tempfx.model.TemperatureUnit;
import fi.metropolia.tempfx.model.TripRecord;
import fi.metropolia.tempfx.model.UnitSystem;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller of {@code main-view.fxml}.
 *
 * <p>It converts temperatures and keeps a trip log (distance, time and the
 * resulting speed) in the database. Every text comes from the resource bundle
 * that the {@code FXMLLoader} was given, so switching language simply means
 * loading the view again with another bundle.
 */
public class MainController implements Initializable {

    @FXML private ComboBox<LanguageOption> languageComboBox;

    @FXML private TextField temperatureField;
    @FXML private ComboBox<TemperatureUnit> fromUnitComboBox;
    @FXML private ComboBox<TemperatureUnit> toUnitComboBox;
    @FXML private Button convertButton;
    @FXML private Label resultLabel;
    @FXML private Label extremeLabel;

    @FXML private TextField tripLabelField;
    @FXML private TextField distanceField;
    @FXML private TextField timeField;
    @FXML private ComboBox<UnitSystem> unitSystemComboBox;
    @FXML private Button addButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;

    @FXML private TableView<TripRecord> tripTable;
    @FXML private TableColumn<TripRecord, String> labelColumn;
    @FXML private TableColumn<TripRecord, String> distanceColumn;
    @FXML private TableColumn<TripRecord, String> timeColumn;
    @FXML private TableColumn<TripRecord, String> speedColumn;
    @FXML private TableColumn<TripRecord, String> unitsColumn;
    @FXML private TableColumn<TripRecord, String> createdColumn;

    @FXML private Label statsLabel;
    @FXML private Label statusLabel;

    private ResourceBundle bundle;
    private Locale locale;
    private Connection connection;
    private UnitSystemDao unitSystemDao;
    private TripRecordDao tripRecordDao;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bundle = resources;
        this.locale = resources.getLocale();

        languageComboBox.getItems().setAll(LocalizationService.options());
        setUpTemperatureUnits();
        setUpUnitSystemConverter();
        setUpTableColumns();

        resultLabel.setText("-");
        extremeLabel.setText("");
    }

    /**
     * Injects the collaborators that the FXML loader cannot provide.
     *
     * @param connection            an open database connection, or {@code null}
     *                              when the database is unreachable
     * @param activeLocale          the locale the view was loaded with
     * @param languageChangeHandler called when the user picks another language
     */
    public void initData(Connection connection, Locale activeLocale,
                         Consumer<Locale> languageChangeHandler) {
        this.connection = connection;
        this.locale = activeLocale;

        LocalizationService.optionFor(activeLocale)
                .ifPresent(option -> languageComboBox.setValue(option));
        // The handler is attached only after the value is set, so selecting the
        // current language during start-up does not trigger a reload.
        languageComboBox.setOnAction(event -> {
            LanguageOption selected = languageComboBox.getValue();
            if (selected != null && languageChangeHandler != null) {
                languageChangeHandler.accept(selected.locale());
            }
        });

        if (connection == null) {
            disableTripSection();
            return;
        }
        this.unitSystemDao = new UnitSystemDao(connection);
        this.tripRecordDao = new TripRecordDao(connection);
        loadUnitSystems();
        refreshTable();
    }

    private void setUpTemperatureUnits() {
        StringConverter<TemperatureUnit> converter = new StringConverter<>() {
            @Override
            public String toString(TemperatureUnit unit) {
                return unit == null ? "" : bundle.getString(unit.getBundleKey());
            }

            @Override
            public TemperatureUnit fromString(String text) {
                return null;
            }
        };
        fromUnitComboBox.setConverter(converter);
        toUnitComboBox.setConverter(converter);
        fromUnitComboBox.getItems().setAll(TemperatureUnit.values());
        toUnitComboBox.getItems().setAll(TemperatureUnit.values());
        fromUnitComboBox.setValue(TemperatureUnit.CELSIUS);
        toUnitComboBox.setValue(TemperatureUnit.FAHRENHEIT);
    }

    private void setUpUnitSystemConverter() {
        unitSystemComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(UnitSystem system) {
                return system == null ? "" : localizedUnitSystem(system);
            }

            @Override
            public UnitSystem fromString(String text) {
                return null;
            }
        });
    }

    private String localizedUnitSystem(UnitSystem system) {
        String key = "unitsystem." + system.code();
        return bundle.containsKey(key) ? bundle.getString(key) : system.code();
    }

    private void setUpTableColumns() {
        labelColumn.setCellValueFactory(row ->
                new SimpleStringProperty(row.getValue().getLabel()));

        distanceColumn.setCellValueFactory(row -> new SimpleStringProperty(
                LocalizationService.formatNumber(row.getValue().getDistance(), locale)
                        + " " + nullSafe(row.getValue().getDistanceUnit())));

        timeColumn.setCellValueFactory(row -> new SimpleStringProperty(
                LocalizationService.formatNumber(row.getValue().getTimeHours(), locale) + " h"));

        speedColumn.setCellValueFactory(row -> new SimpleStringProperty(
                LocalizationService.formatNumber(row.getValue().getSpeed(), locale)
                        + " " + nullSafe(row.getValue().getSpeedUnit())));

        unitsColumn.setCellValueFactory(row -> {
            String code = row.getValue().getUnitSystemCode();
            String key = "unitsystem." + nullSafe(code);
            return new SimpleStringProperty(
                    bundle.containsKey(key) ? bundle.getString(key) : nullSafe(code));
        });

        createdColumn.setCellValueFactory(row -> {
            if (row.getValue().getCreatedAt() == null) {
                return new SimpleStringProperty("");
            }
            DateTimeFormatter formatter = DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale);
            return new SimpleStringProperty(formatter.format(row.getValue().getCreatedAt()));
        });
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void disableTripSection() {
        tripLabelField.setDisable(true);
        distanceField.setDisable(true);
        timeField.setDisable(true);
        unitSystemComboBox.setDisable(true);
        addButton.setDisable(true);
        deleteButton.setDisable(true);
        refreshButton.setDisable(true);
        statusLabel.setText(LocalizationService.message(bundle, "error.database", "no connection"));
    }

    private void loadUnitSystems() {
        try {
            List<UnitSystem> systems = unitSystemDao.findAll();
            unitSystemComboBox.getItems().setAll(systems);
            if (!systems.isEmpty()) {
                unitSystemComboBox.setValue(systems.get(0));
            }
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    @FXML
    private void handleConvert() {
        TemperatureUnit from = fromUnitComboBox.getValue();
        TemperatureUnit to = toUnitComboBox.getValue();
        if (from == null || to == null) {
            showError("error.noSelection");
            return;
        }
        try {
            double value = LocalizationService.parseNumber(temperatureField.getText(), locale);
            double converted = TemperatureConverter.convert(value, from, to);
            resultLabel.setText(
                    LocalizationService.formatNumber(converted, locale) + " " + to.getSymbol());

            boolean extreme = TemperatureConverter
                    .isExtremeTemperature(TemperatureConverter.toCelsius(value, from));
            extremeLabel.setText(bundle.getString(extreme ? "message.extreme" : "message.normal"));
            statusLabel.setText(bundle.getString("status.ready"));
        } catch (IllegalArgumentException invalid) {
            // Covers both unreadable input and temperatures below absolute zero.
            showError("error.invalidNumber");
        }
    }

    @FXML
    private void handleAddTrip() {
        if (tripRecordDao == null) {
            showError("error.database", "no connection");
            return;
        }
        String label = tripLabelField.getText() == null ? "" : tripLabelField.getText().trim();
        if (label.isEmpty()) {
            showError("error.emptyLabel");
            return;
        }
        UnitSystem system = unitSystemComboBox.getValue();
        if (system == null) {
            showError("error.noSelection");
            return;
        }
        try {
            double distance = LocalizationService.parseNumber(distanceField.getText(), locale);
            double hours = LocalizationService.parseNumber(timeField.getText(), locale);
            double speed = SpeedCalculator.speed(distance, hours);

            tripRecordDao.insert(new TripRecord(label, distance, hours, speed, system.id()));
            tripLabelField.clear();
            distanceField.clear();
            timeField.clear();
            refreshTable();
        } catch (IllegalArgumentException invalid) {
            showError("error.invalidNumber");
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    @FXML
    private void handleDeleteTrip() {
        if (tripRecordDao == null) {
            showError("error.database", "no connection");
            return;
        }
        TripRecord selected = tripTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("error.noSelection");
            return;
        }
        try {
            tripRecordDao.deleteById(selected.getId());
            refreshTable();
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshTable();
    }

    private void refreshTable() {
        if (tripRecordDao == null) {
            return;
        }
        try {
            tripTable.setItems(FXCollections.observableArrayList(tripRecordDao.findAll()));
            statsLabel.setText(LocalizationService.message(bundle, "label.stats",
                    tripRecordDao.count(),
                    LocalizationService.formatNumber(tripRecordDao.totalDistance(), locale)));
            statusLabel.setText(bundle.getString("status.ready"));
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    private void showError(String key, Object... arguments) {
        String message = LocalizationService.message(bundle, key, arguments);
        statusLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
