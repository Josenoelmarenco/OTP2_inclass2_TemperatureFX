package fi.metropolia.tempfx.ui;

import fi.metropolia.tempfx.SpeedCalculator;
import fi.metropolia.tempfx.TemperatureConverter;
import fi.metropolia.tempfx.db.ConversionLogDao;
import fi.metropolia.tempfx.db.TripRecordDao;
import fi.metropolia.tempfx.db.UnitSystemDao;
import fi.metropolia.tempfx.db.UnitTranslationDao;
import fi.metropolia.tempfx.i18n.LanguageOption;
import fi.metropolia.tempfx.i18n.LocalizationService;
import fi.metropolia.tempfx.model.ConversionRecord;
import fi.metropolia.tempfx.model.LocalizedUnit;
import fi.metropolia.tempfx.model.TemperatureUnit;
import fi.metropolia.tempfx.model.TripRecord;
import fi.metropolia.tempfx.model.UnitCategory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller of {@code main-view.fxml}.
 *
 * <p>The view is localized on two layers at once, which is the point of this
 * assignment:
 *
 * <ul>
 *   <li><strong>Static labels</strong> (titles, buttons, column headers) come
 *       from the {@code MessagesBundle_xx.properties} file the
 *       {@code FXMLLoader} was given.</li>
 *   <li><strong>Unit names and descriptions</strong> come from the database,
 *       read through {@link UnitTranslationDao} in the selected language, with
 *       a fallback to English.</li>
 * </ul>
 *
 * <p>Picking a language in the selector therefore reloads the resource bundle
 * and re-queries the database in one step.
 */
public class MainController implements Initializable {

    @FXML private ComboBox<LanguageOption> languageComboBox;

    @FXML private TextField temperatureField;
    @FXML private ComboBox<LocalizedUnit> fromUnitComboBox;
    @FXML private ComboBox<LocalizedUnit> toUnitComboBox;
    @FXML private Button convertButton;
    @FXML private Label resultLabel;
    @FXML private Label extremeLabel;
    @FXML private Label fromDescriptionLabel;
    @FXML private Label toDescriptionLabel;

    @FXML private TableView<ConversionRecord> historyTable;
    @FXML private TableColumn<ConversionRecord, String> historyInputColumn;
    @FXML private TableColumn<ConversionRecord, String> historyFromColumn;
    @FXML private TableColumn<ConversionRecord, String> historyToColumn;
    @FXML private TableColumn<ConversionRecord, String> historyResultColumn;
    @FXML private TableColumn<ConversionRecord, String> historyLanguageColumn;
    @FXML private TableColumn<ConversionRecord, String> historyWhenColumn;

    @FXML private TextField tripLabelField;
    @FXML private TextField distanceField;
    @FXML private TextField timeField;
    @FXML private ComboBox<UnitSystem> unitSystemComboBox;
    @FXML private Label systemUnitsLabel;
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
    /** The code stored in the {@code lang} column, for example {@code ja}. */
    private String languageCode = UnitTranslationDao.DEFAULT_LANGUAGE;
    private Connection connection;
    private UnitSystemDao unitSystemDao;
    private TripRecordDao tripRecordDao;
    private UnitTranslationDao unitTranslationDao;
    private ConversionLogDao conversionLogDao;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bundle = resources;
        this.locale = resources.getLocale();
        this.languageCode = UnitTranslationDao.normalize(this.locale);

        languageComboBox.getItems().setAll(LocalizationService.options());
        setUpUnitConverters();
        setUpUnitSystemConverter();
        setUpTripColumns();
        setUpHistoryColumns();

        resultLabel.setText("-");
        extremeLabel.setText("");
        fromDescriptionLabel.setText("");
        toDescriptionLabel.setText("");
        systemUnitsLabel.setText("");
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
        this.languageCode = UnitTranslationDao.normalize(activeLocale);

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
            loadTemperatureUnitsFromBundle();
            disableDatabaseSections();
            return;
        }
        this.unitSystemDao = new UnitSystemDao(connection);
        this.tripRecordDao = new TripRecordDao(connection);
        this.unitTranslationDao = new UnitTranslationDao(connection);
        this.conversionLogDao = new ConversionLogDao(connection);

        loadTemperatureUnitsFromDatabase();
        loadUnitSystems();
        refreshHistory();
        refreshTripTable();
    }

    // ------------------------------------------------------------- unit names

    /**
     * Fills the two temperature drop-downs with the names stored in the
     * database for the active language.
     */
    private void loadTemperatureUnitsFromDatabase() {
        try {
            List<LocalizedUnit> units =
                    unitTranslationDao.findByCategory(UnitCategory.TEMPERATURE, languageCode);
            if (units.isEmpty()) {
                // The tables exist but hold no translations: keep the GUI usable.
                loadTemperatureUnitsFromBundle();
                return;
            }
            fromUnitComboBox.getItems().setAll(units);
            toUnitComboBox.getItems().setAll(units);
            selectUnit(fromUnitComboBox, TemperatureUnit.CELSIUS, units);
            selectUnit(toUnitComboBox, TemperatureUnit.FAHRENHEIT, units);
            statusLabel.setText(bundle.getString("status.ready"));
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
            loadTemperatureUnitsFromBundle();
        }
    }

    /**
     * Fallback used only when the database cannot be reached: the names come
     * from the resource bundle, so the converter itself keeps working.
     */
    private void loadTemperatureUnitsFromBundle() {
        List<LocalizedUnit> units = new ArrayList<>();
        for (TemperatureUnit unit : TemperatureUnit.values()) {
            units.add(new LocalizedUnit(0, unit.getUnitKey(), UnitCategory.TEMPERATURE.name(),
                    unit.getSymbol(), bundle.getString(unit.getBundleKey()), "", false));
        }
        fromUnitComboBox.getItems().setAll(units);
        toUnitComboBox.getItems().setAll(units);
        selectUnit(fromUnitComboBox, TemperatureUnit.CELSIUS, units);
        selectUnit(toUnitComboBox, TemperatureUnit.FAHRENHEIT, units);
    }

    private void selectUnit(ComboBox<LocalizedUnit> comboBox, TemperatureUnit wanted,
                            List<LocalizedUnit> units) {
        units.stream()
                .filter(unit -> wanted.getUnitKey().equals(unit.unitKey()))
                .findFirst()
                .ifPresentOrElse(comboBox::setValue,
                        () -> comboBox.setValue(units.isEmpty() ? null : units.get(0)));
    }

    private void setUpUnitConverters() {
        StringConverter<LocalizedUnit> converter = new StringConverter<>() {
            @Override
            public String toString(LocalizedUnit unit) {
                return unit == null ? "" : unit.nameWithSymbol();
            }

            @Override
            public LocalizedUnit fromString(String text) {
                return null;
            }
        };
        fromUnitComboBox.setConverter(converter);
        toUnitComboBox.setConverter(converter);

        // The description under each drop-down also comes from the database.
        fromUnitComboBox.valueProperty().addListener((observable, previous, selected) ->
                fromDescriptionLabel.setText(describe(selected)));
        toUnitComboBox.valueProperty().addListener((observable, previous, selected) ->
                toDescriptionLabel.setText(describe(selected)));
    }

    /**
     * @param unit the selected unit
     * @return its localized description, marked when the English fallback was
     *         used so the behaviour is visible and not silent
     */
    private String describe(LocalizedUnit unit) {
        if (unit == null || unit.description() == null || unit.description().isBlank()) {
            return "";
        }
        if (unit.usedFallback()) {
            return unit.description() + " " + bundle.getString("label.fallbackNotice");
        }
        return unit.description();
    }

    // ------------------------------------------------------------- conversion

    @FXML
    private void handleConvert() {
        LocalizedUnit fromUnit = fromUnitComboBox.getValue();
        LocalizedUnit toUnit = toUnitComboBox.getValue();
        if (fromUnit == null || toUnit == null) {
            showError("error.noSelection");
            return;
        }
        Optional<TemperatureUnit> from = TemperatureUnit.fromKey(fromUnit.unitKey());
        Optional<TemperatureUnit> to = TemperatureUnit.fromKey(toUnit.unitKey());
        if (from.isEmpty() || to.isEmpty()) {
            showError("error.noSelection");
            return;
        }
        try {
            double value = LocalizationService.parseNumber(temperatureField.getText(), locale);
            double converted = TemperatureConverter.convert(value, from.get(), to.get());
            resultLabel.setText(LocalizationService.formatNumber(converted, locale)
                    + " " + to.get().getSymbol());

            boolean extreme = TemperatureConverter
                    .isExtremeTemperature(TemperatureConverter.toCelsius(value, from.get()));
            extremeLabel.setText(bundle.getString(extreme ? "message.extreme" : "message.normal"));

            storeConversion(value, fromUnit, toUnit, converted);
            statusLabel.setText(bundle.getString("status.ready"));
        } catch (IllegalArgumentException invalid) {
            // Covers both unreadable input and temperatures below absolute zero.
            showError("error.invalidNumber");
        }
    }

    /** Saves the conversion and refreshes the history table. */
    private void storeConversion(double value, LocalizedUnit fromUnit, LocalizedUnit toUnit,
                                 double result) {
        if (conversionLogDao == null || fromUnit.id() == 0 || toUnit.id() == 0) {
            // No database, or units synthesised from the bundle: nothing to log.
            return;
        }
        try {
            conversionLogDao.insert(value, fromUnit.id(), toUnit.id(), result, languageCode);
            refreshHistory();
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    private void setUpHistoryColumns() {
        historyInputColumn.setCellValueFactory(row -> new SimpleStringProperty(
                LocalizationService.formatNumber(row.getValue().inputValue(), locale)));
        historyFromColumn.setCellValueFactory(row ->
                new SimpleStringProperty(nullSafe(row.getValue().fromUnitName())));
        historyToColumn.setCellValueFactory(row ->
                new SimpleStringProperty(nullSafe(row.getValue().toUnitName())));
        historyResultColumn.setCellValueFactory(row -> new SimpleStringProperty(
                LocalizationService.formatNumber(row.getValue().resultValue(), locale)));
        historyLanguageColumn.setCellValueFactory(row ->
                new SimpleStringProperty(nullSafe(row.getValue().lang())));
        historyWhenColumn.setCellValueFactory(row -> {
            if (row.getValue().createdAt() == null) {
                return new SimpleStringProperty("");
            }
            return new SimpleStringProperty(DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(row.getValue().createdAt()));
        });
    }

    private void refreshHistory() {
        if (conversionLogDao == null) {
            return;
        }
        try {
            historyTable.setItems(FXCollections.observableArrayList(
                    conversionLogDao.findRecent(languageCode)));
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    // ------------------------------------------------------------- trip log

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
        unitSystemComboBox.valueProperty().addListener((observable, previous, selected) ->
                systemUnitsLabel.setText(describeSystemUnits(selected)));
    }

    private String localizedUnitSystem(UnitSystem system) {
        String key = "unitsystem." + system.code();
        return bundle.containsKey(key) ? bundle.getString(key) : system.code();
    }

    /**
     * Shows the distance and speed units of the selected measurement system,
     * named in the active language and read from the database through the
     * foreign key {@code measurement_unit.unit_system_id}.
     *
     * @param system the selected measurement system
     * @return a short, localized summary, or an empty string when unavailable
     */
    private String describeSystemUnits(UnitSystem system) {
        if (system == null || unitTranslationDao == null) {
            return "";
        }
        try {
            List<String> names = new ArrayList<>();
            for (UnitCategory category : List.of(UnitCategory.DISTANCE, UnitCategory.SPEED)) {
                unitTranslationDao.findByCategory(category, languageCode).stream()
                        .filter(unit -> unit.id() != 0)
                        .filter(unit -> belongsTo(unit, system))
                        .findFirst()
                        .ifPresent(unit -> names.add(unit.nameWithSymbol()));
            }
            return String.join(" · ", names);
        } catch (SQLException cause) {
            return "";
        }
    }

    private boolean belongsTo(LocalizedUnit unit, UnitSystem system) {
        // The symbol stored on the unit system is the link the schema already had.
        return unit.symbol().equals(system.distanceUnit())
                || unit.symbol().equals(system.speedUnit());
    }

    private void setUpTripColumns() {
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

    private void disableDatabaseSections() {
        tripLabelField.setDisable(true);
        distanceField.setDisable(true);
        timeField.setDisable(true);
        unitSystemComboBox.setDisable(true);
        addButton.setDisable(true);
        deleteButton.setDisable(true);
        refreshButton.setDisable(true);
        historyTable.setDisable(true);
        statusLabel.setText(bundle.getString("status.offline"));
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
            refreshTripTable();
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
            refreshTripTable();
        } catch (SQLException cause) {
            showError("error.database", cause.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshTripTable();
        refreshHistory();
    }

    private void refreshTripTable() {
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
