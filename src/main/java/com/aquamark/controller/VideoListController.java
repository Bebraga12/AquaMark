package com.aquamark.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class VideoListController {

    @FXML private ListView<File> listVideos;
    @FXML private Label lblCount;
    @FXML private Button btnRemove;
    @FXML private Button btnClear;

    private final ObservableList<File> videos = FXCollections.observableArrayList();
    private Consumer<File> onVideoSelected;

    @FXML
    public void initialize() {
        listVideos.setItems(videos);
        listVideos.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(file.getName());
                    setTooltip(new Tooltip(file.getAbsolutePath()));
                }
            }
        });

        listVideos.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onVideoSelected != null) {
                onVideoSelected.accept(newVal);
            }
        });

        btnRemove.disableProperty().bind(
            listVideos.getSelectionModel().selectedItemProperty().isNull()
        );
        btnClear.disableProperty().bind(
            javafx.beans.binding.Bindings.isEmpty(videos)
        );

        updateCount();
    }

    @FXML
    private void onAddVideos() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Adicionar Videos");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.m4v")
        );
        Stage stage = (Stage) listVideos.getScene().getWindow();
        List<File> files = chooser.showOpenMultipleDialog(stage);
        addVideos(files);
    }

    @FXML
    private void onRemove() {
        File selected = listVideos.getSelectionModel().getSelectedItem();
        if (selected != null) {
            videos.remove(selected);
            updateCount();
        }
    }

    @FXML
    private void onClear() {
        videos.clear();
        updateCount();
    }

    public void addVideos(List<File> files) {
        if (files == null) return;
        for (File f : files) {
            if (!videos.contains(f)) {
                videos.add(f);
            }
        }
        updateCount();
    }

    public void setOnVideoSelected(Consumer<File> callback) {
        this.onVideoSelected = callback;
    }

    public ObservableList<File> getVideos() {
        return videos;
    }

    private void updateCount() {
        int n = videos.size();
        lblCount.setText(n + " " + (n == 1 ? "item" : "itens"));
    }
}
