package io.github.autocompletedemo;

import io.github.autocomplete.AutocompleteProvider;
import io.github.autocomplete.TextAnalyzer;
import io.github.autocomplete.config.AutocompleteConfig;
import io.github.autocomplete.model.Candidate;
import io.github.autocomplete.tokenizer.SimpleTokenizer;
import io.github.autocomplete.tokenizer.Tokenizer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class App extends Application {

  private TextArea textArea;
  private ListView<String> suggestionsList;
  private Popup suggestionsPopup;
  private VBox root;
  private MenuBar menuBar;
  private Label statusBar;

  // Autocomplete компоненты
  private TextAnalyzer textAnalyzer;
  private AutocompleteProvider autocompleteProvider;
  private AutocompleteConfig autocompleteConfig;

  // Обработка файлов
  private File currentFile;
  private boolean isModified = false;

  // Настройки
  private int maxSuggestions = 10;
  private int toleranceThreshold = 0;
  private int tolerance = 0;
  private double similarWeight = 0.5;
  private double originalWeight = 1.0;

  private boolean suggestionsDisabled = false;

  @Override
  public void start(Stage primaryStage) {
    initializeAutocomplete();
    createUI();
    setupEventHandlers();
    loadDefaultDictionary();

    Scene scene = new Scene(root, 800, 600);
    primaryStage.setTitle("Untitled - Autocomplete Text Editor");
    primaryStage.setScene(scene);

    setupWindowResizeHandlers(primaryStage);

    primaryStage.show();

    // Обработка закрытия окна
    primaryStage.setOnCloseRequest(event -> {
      if (isModified) {
        event.consume();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Save Changes");
        alert.setHeaderText("Document has been modified");
        alert.setContentText("Do you want to save your changes before closing?");

        ButtonType saveButton = new ButtonType("Save");
        ButtonType dontSaveButton = new ButtonType("Don't Save");
        ButtonType cancelButton = new ButtonType("Cancel");

        alert.getButtonTypes().setAll(saveButton, dontSaveButton, cancelButton);

        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initOwner(primaryStage);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
          if (result.get() == saveButton) {
            saveFile();
            Platform.exit();
          } else if (result.get() == dontSaveButton) {
            Platform.exit();
          }
        }
      }
    });
  }

  private void initializeAutocomplete() {
    autocompleteConfig = new AutocompleteConfig();
    Tokenizer tokenizer = new SimpleTokenizer();
    textAnalyzer = new TextAnalyzer(tokenizer);
    autocompleteProvider = new AutocompleteProvider(textAnalyzer, autocompleteConfig);
  }

  private void createUI() {
    createMenuBar();

    textArea = new TextArea();
    textArea.setPromptText("Start typing to see autocomplete suggestions...");
    textArea.setWrapText(true);

    // Popup с предложениями для автозаполнения
    suggestionsPopup = new Popup();
    suggestionsList = new ListView<>();
    suggestionsList.setPrefHeight(150);
    suggestionsList.setMaxHeight(150);
    suggestionsList.setPrefWidth(200);
    suggestionsList.setMaxWidth(300);

    // Стили для списка предложений
    suggestionsList
        .setStyle("-fx-background-color: white; -fx-border-color: #ccc; -fx-border-width: 1px;");

    // Обработка нажатия на Tab на списке предложений
    suggestionsList.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.TAB) {
        event.consume();
        String selected = suggestionsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
          insertSuggestion(selected);
        }
      } else if (event.getCode() == KeyCode.ESCAPE) {
        event.consume();
        hideSuggestions();
      }
    });

    suggestionsList.setCellFactory(listView -> new ListCell<>() {
      private void updateHighlight() {
        if (isSelected()) {
          setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: black;");
        } else {
          setStyle("-fx-background-color: transparent; -fx-text-fill: black;");
        }
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setStyle("");
        } else {
          setText(item);
          updateHighlight();
        }
      }

      {
        selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
          updateHighlight();
        });
        setOnMouseEntered(event -> {
          suggestionsList.getSelectionModel().select(getIndex());
        });
      }
    });

    suggestionsPopup.getContent().add(suggestionsList);

    createStatusBar();

    VBox editorContainer = new VBox(5);
    editorContainer.getChildren().addAll(textArea);
    VBox.setVgrow(textArea, Priority.ALWAYS);

    root = new VBox(5);
    root.setPadding(new Insets(5));
    root.getChildren().addAll(menuBar, editorContainer, statusBar);
    VBox.setVgrow(editorContainer, Priority.ALWAYS);
  }

  private void createMenuBar() {
    menuBar = new MenuBar();

    Menu fileMenu = new Menu("File");
    MenuItem newFile = new MenuItem("New");
    MenuItem openFile = new MenuItem("Open...");
    MenuItem saveFile = new MenuItem("Save");
    MenuItem saveAsFile = new MenuItem("Save As...");
    SeparatorMenuItem separator1 = new SeparatorMenuItem();
    MenuItem exit = new MenuItem("Exit");

    fileMenu.getItems().addAll(newFile, openFile, saveFile, saveAsFile, separator1, exit);

    Menu dictionaryMenu = new Menu("Dictionary");
    MenuItem loadDictionary = new MenuItem("Load Dictionary...");
    MenuItem saveDictionary = new MenuItem("Save Dictionary...");
    MenuItem clearDictionary = new MenuItem("Clear Dictionary");
    MenuItem addTextToDictionary = new MenuItem("Add Current Text to Dictionary");

    dictionaryMenu.getItems().addAll(loadDictionary, saveDictionary, clearDictionary,
        addTextToDictionary);

    Menu settingsMenu = new Menu("Settings");
    MenuItem autocompleteSettings = new MenuItem("Autocomplete Settings...");

    settingsMenu.getItems().addAll(autocompleteSettings);

    Menu helpMenu = new Menu("Help");
    MenuItem about = new MenuItem("About");
    helpMenu.getItems().add(about);

    menuBar.getMenus().addAll(fileMenu, dictionaryMenu, settingsMenu, helpMenu);

    // Обработка событий
    newFile.setOnAction(e -> newFile());
    openFile.setOnAction(e -> openFile());
    saveFile.setOnAction(e -> saveFile());
    saveAsFile.setOnAction(e -> saveAsFile());
    exit.setOnAction(e -> Platform.exit());

    loadDictionary.setOnAction(e -> loadDictionary());
    saveDictionary.setOnAction(e -> saveDictionary());
    clearDictionary.setOnAction(e -> clearDictionary());
    addTextToDictionary.setOnAction(e -> addTextToDictionary());

    autocompleteSettings.setOnAction(e -> showAutocompleteSettings());

    about.setOnAction(e -> showAbout());
  }

  private void createStatusBar() {
    statusBar = new Label();
    statusBar.setText("Ready");
  }

  private void setupEventHandlers() {
    textArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    textArea.setOnKeyReleased(this::handleKeyRelease);
    textArea.textProperty().addListener((observable, oldValue, newValue) -> {
      isModified = true;
      updateStatusBar();
      updateWindowTitle();
    });

    textArea.focusedProperty().addListener((observable, oldValue, newValue) -> {
      if (!newValue) {
        hideSuggestions();
      }
    });

    suggestionsList.setOnMouseClicked(event -> {
      String selected = suggestionsList.getSelectionModel().getSelectedItem();
      if (selected != null) {
        insertSuggestion(selected);
      }
    });

    suggestionsList.setOnMousePressed(event -> {
      if (event.getClickCount() == 2) {
        String selected = suggestionsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
          insertSuggestion(selected);
        }
      }
    });
  }

  private void setupWindowResizeHandlers(Stage stage) {
    stage.widthProperty().addListener((obs, oldWidth, newWidth) -> hideSuggestions());
    stage.heightProperty().addListener((obs, oldHeight, newHeight) -> hideSuggestions());
  }

  private void handleKeyPress(KeyEvent event) {
    if (event.getCode() == KeyCode.TAB) {
      if (suggestionsPopup.isShowing()) {
        event.consume();
        String selected = suggestionsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
          insertSuggestion(selected);
        }
      }
    } else if (event.getCode() == KeyCode.ESCAPE) {
      if (suggestionsPopup.isShowing()) {
        event.consume();
        hideSuggestions();
      }
    } else if (event.getCode() == KeyCode.UP) {
      if (suggestionsPopup.isShowing()) {
        event.consume();
        suggestionsList.getSelectionModel().selectPrevious();
      }
    } else if (event.getCode() == KeyCode.DOWN) {
      if (suggestionsPopup.isShowing()) {
        event.consume();
        suggestionsList.getSelectionModel().selectNext();
      }
    }
  }

  private void handleKeyRelease(KeyEvent event) {
    if (!event.getCode().isNavigationKey() && !event.getCode().isFunctionKey()) {
      updateSuggestions();
    }
  }

  private void updateSuggestions() {
    if (suggestionsDisabled) {
      return;
    }

    String currentText = textArea.getText();
    int caretPosition = textArea.getCaretPosition();

    String currentWord = getCurrentWord(currentText, caretPosition);

    if (currentWord.length() > 0) {
      try {
        List<Candidate> candidates =
            autocompleteProvider.getAutocomplete(currentWord, maxSuggestions);
        if (!candidates.isEmpty()) {
          ObservableList<String> suggestions = FXCollections.observableArrayList();
          for (Candidate candidate : candidates) {
            suggestions.add(candidate.word());
          }
          suggestionsList.setItems(suggestions);
          suggestionsList.getSelectionModel().selectFirst();
          showSuggestions();
        } else {
          hideSuggestions();
        }
      } catch (Exception e) {
        hideSuggestions();
      }
    } else {
      hideSuggestions();
    }
  }

  private void showSuggestions() {
    if (!suggestionsPopup.isShowing()) {
      Point2D caretPosition = getCaretScreenPosition();
      if (caretPosition != null) {
        suggestionsPopup.show(textArea, caretPosition.getX(), caretPosition.getY() + 20);
        suggestionsList.requestFocus();
      }
    }
  }

  private void hideSuggestions() {
    if (suggestionsPopup.isShowing()) {
      suggestionsPopup.hide();
      suggestionsDisabled = true;
      new Thread(() -> {
        try {
          Thread.sleep(300);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        Platform.runLater(() -> suggestionsDisabled = false);
      }).start();
    }
  }

  private Point2D getCaretScreenPosition() {
    try {
      Bounds textAreaBounds = textArea.localToScreen(textArea.getBoundsInLocal());
      if (textAreaBounds == null) {
        return null;
      }

      int caretPosition = textArea.getCaretPosition();
      String text = textArea.getText();

      int line = 0;
      int column = 0;
      for (int i = 0; i < caretPosition && i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
          line++;
          column = 0;
        } else {
          column++;
        }
      }

      double charWidth = 6.0;
      double lineHeight = 16.0;

      double x = textAreaBounds.getMinX() + 10 + (column * charWidth);
      double y = textAreaBounds.getMinY() + 10 + (line * lineHeight);

      double popupWidth = 250;
      double popupHeight = 150;

      if (x + popupWidth > textAreaBounds.getMaxX()) {
        x = textAreaBounds.getMaxX() - popupWidth - 10;
      }
      if (y + popupHeight > textAreaBounds.getMaxY()) {
        y = textAreaBounds.getMinY() - popupHeight - 10;
      }

      return new Point2D(x, y);
    } catch (Exception e) {
      Bounds bounds = textArea.localToScreen(textArea.getBoundsInLocal());
      if (bounds != null) {
        return new Point2D(bounds.getMinX() + 10, bounds.getMinY() + 30);
      }
      return null;
    }
  }

  private String getCurrentWord(String text, int caretPosition) {
    if (caretPosition == 0)
      return "";

    int start = caretPosition - 1;
    while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
      start--;
    }
    start++;

    return text.substring(start, caretPosition);
  }

  private void insertSuggestion(String suggestion) {
    String currentText = textArea.getText();
    int caretPosition = textArea.getCaretPosition();

    int start = caretPosition - 1;
    while (start >= 0 && Character.isLetterOrDigit(currentText.charAt(start))) {
      start--;
    }
    start++;

    String newText =
        currentText.substring(0, start) + suggestion + currentText.substring(caretPosition);

    textArea.setText(newText);
    textArea.positionCaret(start + suggestion.length());
    hideSuggestions();
  }

  // Операции с файлами
  private void newFile() {
    if (isModified) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("New File");
      alert.setHeaderText("Document has been modified");
      alert.setContentText("Do you want to save your changes before creating a new file?");

      ButtonType saveButton = new ButtonType("Save");
      ButtonType dontSaveButton = new ButtonType("Don't Save");
      ButtonType cancelButton = new ButtonType("Cancel");

      alert.getButtonTypes().setAll(saveButton, dontSaveButton, cancelButton);

      alert.initModality(Modality.APPLICATION_MODAL);
      alert.initOwner(textArea.getScene().getWindow());

      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent()) {
        if (result.get() == saveButton) {
          saveFile();
          textArea.clear();
          currentFile = null;
          isModified = false;
          updateStatusBar();
          updateWindowTitle();
        } else if (result.get() == dontSaveButton) {
          textArea.clear();
          currentFile = null;
          isModified = false;
          updateStatusBar();
          updateWindowTitle();
        }
      }
    } else {
      textArea.clear();
      currentFile = null;
      isModified = false;
      updateStatusBar();
      updateWindowTitle();
    }
  }

  private void openFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Open File");
    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"),
        new FileChooser.ExtensionFilter("All Files", "*.*"));

    File file = fileChooser.showOpenDialog(textArea.getScene().getWindow());
    if (file != null) {
      try {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        textArea.setText(content);
        currentFile = file;
        isModified = false;
        updateStatusBar();
        updateWindowTitle();
      } catch (IOException e) {
        showError("Error opening file", e.getMessage());
      }
    }
  }

  private void saveFile() {
    if (currentFile == null) {
      saveAsFile();
    } else {
      try {
        Files.write(currentFile.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
        isModified = false;
        updateStatusBar();
        updateWindowTitle();
      } catch (IOException e) {
        showError("Error saving file", e.getMessage());
      }
    }
  }

  private void saveAsFile() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Save File");
    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"),
        new FileChooser.ExtensionFilter("All Files", "*.*"));

    File file = fileChooser.showSaveDialog(textArea.getScene().getWindow());
    if (file != null) {
      try {
        Files.write(file.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
        currentFile = file;
        isModified = false;
        updateStatusBar();
        updateWindowTitle();
      } catch (IOException e) {
        showError("Error saving file", e.getMessage());
      }
    }
  }

  // Операции со словарем
  private void loadDictionary() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Load Dictionary");
    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Files", "*.*"));

    File file = fileChooser.showOpenDialog(textArea.getScene().getWindow());
    if (file != null) {
      try {
        textAnalyzer.loadFromFile(file);
        statusBar.setText("Dictionary loaded from: " + file.getName());
      } catch (IOException e) {
        showError("Error loading dictionary", e.getMessage());
      }
    }
  }

  private void saveDictionary() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Save Dictionary");
    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Files", "*.*"));

    File file = fileChooser.showSaveDialog(textArea.getScene().getWindow());
    if (file != null) {
      try {
        textAnalyzer.saveToFile(file);
        statusBar.setText("Dictionary saved to: " + file.getName());
      } catch (IOException e) {
        showError("Error saving dictionary", e.getMessage());
      }
    }
  }

  private void clearDictionary() {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Clear Dictionary");
    alert.setHeaderText("Clear Dictionary");
    alert.setContentText("Are you sure you want to clear the dictionary?");

    Optional<ButtonType> result = alert.showAndWait();
    if (result.isPresent() && result.get() == ButtonType.OK) {
      textAnalyzer.clear();
      statusBar.setText("Dictionary cleared");
    }
  }

  private void addTextToDictionary() {
    String currentText = textArea.getText();
    if (!currentText.isEmpty()) {
      textAnalyzer.addText(currentText);
      statusBar.setText("Current text added to dictionary");
    }
  }

  private void loadDefaultDictionary() {
    File dictFile = new File("dict");
    if (dictFile.exists() && dictFile.isFile()) {
      try {
        textAnalyzer.loadFromFile(dictFile);
        statusBar.setText("Default dictionary loaded from: dict");
      } catch (IOException e) {
        showError("Error loading default dictionary", e.getMessage());
      }
    } else {
      statusBar.setText("Default dictionary file 'dict' not found.");
    }
  }

  // Диалоги настроек
  private void showAutocompleteSettings() {
    Stage settingsStage = new Stage();
    settingsStage.initModality(Modality.APPLICATION_MODAL);
    settingsStage.setTitle("Autocomplete Settings");

    VBox settingsPane = new VBox(10);
    settingsPane.setPadding(new Insets(20));

    Label maxSuggestionsLabel = new Label("Maximum suggestions:");
    Spinner<Integer> maxSuggestionsSpinner = new Spinner<>(1, 50, maxSuggestions);
    maxSuggestionsSpinner.setEditable(true);

    Label toleranceThresholdLabel = new Label("Tolerance threshold:");
    Spinner<Integer> toleranceThresholdSpinner = new Spinner<>(0, 100, toleranceThreshold);
    toleranceThresholdSpinner.setEditable(true);

    Label toleranceLabel = new Label("Tolerance:");
    Spinner<Integer> toleranceSpinner = new Spinner<>(0, 10, tolerance);
    toleranceSpinner.setEditable(true);

    Label similarWeightLabel = new Label("Similar weight:");
    Spinner<Double> similarWeightSpinner = new Spinner<>(0.0, 2.0, similarWeight, 0.1);
    similarWeightSpinner.setEditable(true);

    Label originalWeightLabel = new Label("Original weight:");
    Spinner<Double> originalWeightSpinner = new Spinner<>(0.0, 2.0, originalWeight, 0.1);
    originalWeightSpinner.setEditable(true);

    HBox buttons = new HBox(10);
    Button applyButton = new Button("Apply");
    Button cancelButton = new Button("Cancel");
    buttons.getChildren().addAll(applyButton, cancelButton);

    applyButton.setOnAction(e -> {
      maxSuggestions = maxSuggestionsSpinner.getValue();
      toleranceThreshold = toleranceThresholdSpinner.getValue();
      tolerance = toleranceSpinner.getValue();
      similarWeight = similarWeightSpinner.getValue();
      originalWeight = originalWeightSpinner.getValue();

      BiFunction<String, String, Integer> distanceFunction =
          (s1, s2) -> io.github.autocomplete.util.Levenshtein.distance(s1, s2);
      autocompleteConfig = new AutocompleteConfig(distanceFunction, toleranceThreshold, tolerance,
          similarWeight, originalWeight);
      autocompleteProvider.setConfig(autocompleteConfig);

      settingsStage.close();
      statusBar.setText("Autocomplete settings updated");
    });

    cancelButton.setOnAction(e -> settingsStage.close());

    settingsPane.getChildren().addAll(maxSuggestionsLabel, maxSuggestionsSpinner,
        toleranceThresholdLabel, toleranceThresholdSpinner, toleranceLabel, toleranceSpinner,
        similarWeightLabel, similarWeightSpinner, originalWeightLabel, originalWeightSpinner,
        buttons);

    Scene settingsScene = new Scene(settingsPane);
    settingsStage.setScene(settingsScene);
    settingsStage.showAndWait();
  }

  private void showAbout() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("About");
    alert.setHeaderText("Autocomplete Text Editor");
    alert.setContentText(
        "Простой текстовый редактор с функцией автодополнения в реальном времени.\n\n"
            + "Возможности:\n" + "• Предложения автодополнения в реальном времени\n"
            + "• Управление словарями\n" + "• Настраиваемые параметры автодополнения\n"
            + "• Операции с файлами (Новый, Открыть, Сохранить)\n"
            + "• Горячие клавиши (Tab, Escape, стрелки)\n\n" + "Версия: 1.0");
    alert.showAndWait();
  }

  private void updateStatusBar() {
    String status = "Ready";
    if (currentFile != null) {
      status = "File: " + currentFile.getName();
    }
    if (isModified) {
      status += " (Modified)";
    }
    statusBar.setText(status);
  }

  private void updateWindowTitle() {
    String title;
    if (currentFile != null) {
      title = currentFile.getName() + " - Autocomplete Text Editor";
    } else {
      title = "Untitled - Autocomplete Text Editor";
    }
    if (isModified) {
      title += " *";
    }
    ((Stage) textArea.getScene().getWindow()).setTitle(title);
  }

  private void showError(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
