package com.aquamark;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AquaMarkApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
        stage.setTitle("AquaMark");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(560);

        javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setWidth(Math.min(1440, bounds.getWidth() * 0.92));
        stage.setHeight(Math.min(820, bounds.getHeight() * 0.92));
        stage.setX(bounds.getMinX() + (bounds.getWidth()  - stage.getWidth())  / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
