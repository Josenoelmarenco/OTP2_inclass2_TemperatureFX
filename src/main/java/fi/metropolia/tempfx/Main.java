package fi.metropolia.tempfx;

/**
 * Plain launcher.
 *
 * <p>A class that does not extend {@link javafx.application.Application} is
 * used as the jar main class, which is the usual way of starting a JavaFX
 * application from an executable jar.
 */
public final class Main {

    private Main() {
        // Entry point only.
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
