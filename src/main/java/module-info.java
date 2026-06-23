module com.aquamark {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;

    exports com.aquamark;
    opens com.aquamark to javafx.fxml, javafx.graphics;
    opens com.aquamark.controller to javafx.fxml;
    opens com.aquamark.model to javafx.fxml;
}
