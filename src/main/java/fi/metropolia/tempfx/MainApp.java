package fi.metropolia.tempfx;

import fi.metropolia.tempfx.db.Database;
import fi.metropolia.tempfx.i18n.LocalizationService;
import fi.metropolia.tempfx.ui.MainController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * JavaFX entry point of the Temperature Converter and Trip Log.
 *
 * <p>Switching language reloads {@code main-view.fxml} with another
 * {@link ResourceBundle} and swaps the scene root, which is the standard way
 * of doing dynamic localization with FXML. For Arabic the node orientation is
 * flipped so the entire layout is mirrored right to left.
 *
 * <p>The start-up language can be chosen with the first command line argument
 * or with the {@code APP_LANG} environment variable, for example {@code ar}.
 */
public class MainApp extends Application {

    private static final String VIEW = "/fi/metropolia/tempfx/main-view.fxml";
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 720;

    private Connection connection;
    private Stage stage;

    @Override
    public void init() {
        try {
            connection = Database.getConnection();
            Database.initialize(connection);
        } catch (SQLException cause) {
            // The user interface still opens; the trip section is disabled.
            System.err.println("Database is not available: " + cause.getMessage());
            connection = null;
        }
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.stage = primaryStage;
        showView(startLocale());
        primaryStage.show();
    }

    /** @return the language asked for on the command line, or the default one */
    private Locale startLocale() {
        List<String> raw = getParameters() == null ? List.of() : getParameters().getRaw();
        String requested = raw.isEmpty() ? System.getenv("APP_LANG") : raw.get(0);
        return LocalizationService.byLanguageCode(requested)
                .orElseGet(LocalizationService::defaultLocale);
    }

    /**
     * Loads the view with the resource bundle of one language.
     *
     * @param locale the language to show the interface in
     * @throws IOException when the FXML file cannot be read
     */
    private void showView(Locale locale) throws IOException {
        ResourceBundle bundle = LocalizationService.bundle(locale);
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(VIEW), bundle);
        Parent root = loader.load();

        root.setNodeOrientation(LocalizationService.isRightToLeft(locale)
                ? NodeOrientation.RIGHT_TO_LEFT
                : NodeOrientation.LEFT_TO_RIGHT);

        MainController controller = loader.getController();
        controller.initData(connection, locale, this::switchLanguage);

        stage.setTitle(bundle.getString("app.title"));
        Scene scene = stage.getScene();
        if (scene == null) {
            stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
        } else {
            scene.setRoot(root);
        }
    }

    /** Called by the controller when the user picks another language. */
    private void switchLanguage(Locale locale) {
        try {
            showView(locale);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    @Override
    public void stop() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
