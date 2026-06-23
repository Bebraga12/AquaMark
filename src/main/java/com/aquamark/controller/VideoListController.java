package com.aquamark.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class VideoListController {

    private static final Set<String> VIDEO_EXTENSIONS =
        Set.of("mp4", "mov", "avi", "mkv", "webm", "m4v");

    @FXML private ListView<File> listVideos;
    @FXML private Label          lblCount;
    @FXML private Button         btnAdd;
    @FXML private Button         btnRemove;

    private final ObservableList<File> videos = FXCollections.observableArrayList();
    private Consumer<File> onVideoSelected;

    private ContextMenu menuAdd;
    private ContextMenu menuRemove;

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
            if (newVal != null && onVideoSelected != null) onVideoSelected.accept(newVal);
        });

        buildMenuAdd();
        buildMenuRemove();
        updateCount();
    }

    // ── Context menus ─────────────────────────────────────────────

    private void buildMenuAdd() {
        MenuItem miVideos = new MenuItem("Selecionar Vídeo(s)");
        MenuItem miPasta  = new MenuItem("Adicionar Pasta");
        miVideos.setOnAction(e -> pickFiles());
        miPasta.setOnAction(e -> pickFolder());
        menuAdd = new ContextMenu(miVideos, miPasta);
    }

    private void buildMenuRemove() {
        MenuItem miSelecionado = new MenuItem("Remover Selecionado");
        MenuItem miTudo        = new MenuItem("Remover Tudo");
        miSelecionado.setOnAction(e -> removeSelected());
        miTudo.setOnAction(e -> removeAll());
        menuRemove = new ContextMenu(miSelecionado, miTudo);

        menuRemove.setOnShowing(e -> {
            miSelecionado.setDisable(listVideos.getSelectionModel().getSelectedItem() == null);
            miTudo.setDisable(videos.isEmpty());
        });
    }

    @FXML private void onAdd()    { menuAdd.show(btnAdd,       javafx.geometry.Side.BOTTOM, 0, 2); }
    @FXML private void onRemove() { menuRemove.show(btnRemove, javafx.geometry.Side.BOTTOM, 0, 2); }

    private void pickFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Vídeos");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Vídeos", "*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.m4v")
        );
        Stage stage = (Stage) listVideos.getScene().getWindow();
        addVideos(chooser.showOpenMultipleDialog(stage));
    }

    private void pickFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Adicionar Pasta de Vídeos");
        Stage stage = (Stage) listVideos.getScene().getWindow();
        File dir = chooser.showDialog(stage);
        if (dir == null) return;
        File[] found = dir.listFiles(f ->
            f.isFile() && VIDEO_EXTENSIONS.contains(extension(f.getName())));
        if (found != null) addVideos(Arrays.asList(found));
    }

    private void removeSelected() {
        File sel = listVideos.getSelectionModel().getSelectedItem();
        if (sel != null) { videos.remove(sel); updateCount(); }
    }

    private void removeAll() { videos.clear(); updateCount(); }

    // ── Public API ────────────────────────────────────────────────

    public void addVideos(List<File> files) {
        if (files == null) return;
        for (File f : files) if (!videos.contains(f)) videos.add(f);
        updateCount();
    }

    public void setOnVideoSelected(Consumer<File> callback) { this.onVideoSelected = callback; }
    public ObservableList<File> getVideos()                 { return videos; }

    // ── Helpers ───────────────────────────────────────────────────

    private void updateCount() {
        int n = videos.size();
        lblCount.setText(n + " " + (n == 1 ? "item" : "itens"));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
