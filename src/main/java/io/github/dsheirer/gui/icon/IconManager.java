/*
 * *****************************************************************************
 * Copyright (C) 2014-2025 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.gui.icon;

import io.github.dsheirer.gui.playlist.Editor;
import io.github.dsheirer.icon.Icon;
import io.github.dsheirer.icon.IconModel;
import javafx.beans.binding.Bindings;
import javafx.collections.transformation.SortedList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class IconManager extends Editor<Icon>
{
    private final static Logger mLog = LoggerFactory.getLogger(IconManager.class);

    private TableView<Icon> mIconTableView;
    private IconModel mIconModel;
    private FileChooser mFileChooser;
    private TextField mName;
    private Button mPathButton;
    private SortedList<Icon> mIconSortedList;

    public IconManager(IconModel iconModel)
    {
        mIconModel = iconModel;

        VBox layout = new VBox();
        layout.getChildren().add(getIconTableView());
        VBox.setVgrow(getIconTableView(), Priority.ALWAYS);

        GridPane editPanel = new GridPane();
        editPanel.setHgap(5.0);
        editPanel.setVgap(5.0);

        Label nameLabel = new Label("Name");
        editPanel.add(nameLabel, 0, 0);

        mName = new TextField();
        GridPane.setHgrow(mName, Priority.ALWAYS);
        editPanel.add(mName, 1, 0);

        Label fileLabel = new Label("Path");
        GridPane.setHalignment(fileLabel, HPos.RIGHT);
        editPanel.add(fileLabel, 0, 1);

        mPathButton = new Button("Select File ...");
        mPathButton.setOnAction(event -> {
            if(mFileChooser == null) {
                mFileChooser = new FileChooser();
                mFileChooser.setTitle("Select Icon Image File");
                mFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png"));
            }
            File file = mFileChooser.showOpenDialog(IconManager.this.getScene().getWindow());
            if(file != null) { mPathButton.setText(file.getAbsolutePath()); }
        });
        mPathButton.setMinWidth(250.0);
        mPathButton.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(mPathButton, Priority.ALWAYS);
        editPanel.add(mPathButton, 1, 1);

        HBox buttonPanel = new HBox();
        buttonPanel.setSpacing(5.0);
        buttonPanel.setPadding(new Insets(10.0, 10.0, 10.0, 10.0));
        buttonPanel.setAlignment(Pos.CENTER);

        layout.getChildren().addAll(editPanel, buttonPanel);
        setEditorNode(layout);
    }

    private SortedList<Icon> getIconSortedList()
    {
        if(mIconSortedList == null) {
            mIconSortedList = new SortedList<>(mIconModel.iconsProperty(), (o1, o2) -> {
                if(o1 != null && o2 != null) { return o1.getName().compareTo(o2.getName()); }
                return 0;
            });
        }
        return mIconSortedList;
    }

    private TableView<Icon> getIconTableView()
    {
        if(mIconTableView == null) {
            mIconTableView = new TableView<>(getIconSortedList());
            mIconTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<Icon,String> iconColumn = new TableColumn<>("Icon");
            iconColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
            iconColumn.setCellFactory(new IconTableCellFactory());

            TableColumn<Icon,String> nameColumn = new TableColumn<>("Name");
            nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

            mIconTableView.getColumns().addAll(iconColumn, nameColumn);
            mIconTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> populateEditor(newValue));
        }
        return mIconTableView;
    }

    @Override
    public void dispose() { }

    protected void doCancel() { populateEditor(getIconTableView().getSelectionModel().getSelectedItem()); }

    protected void doNew() { populateEditor(null); mName.requestFocus(); }

    @Override
    protected TableView<Icon> getTableView() { return getIconTableView(); }

    protected void doSave() {
        String name = mName.getText().trim();
        String path = mPathButton.getText();
        File file = new File(path);
        if(file.exists()) {
            try {
                mIconModel.createIcon(name, file.toPath());
            } catch (Exception e) { mLog.error("Error saving icon", e); }
        }
    }

    protected void doDelete() {
        Icon icon = getIconTableView().getSelectionModel().getSelectedItem();
        if(icon != null) {
            try { mIconModel.deleteIcon(icon); }
            catch(Exception e) { mLog.error("Error deleting icon", e); }
        }
    }

    private void populateEditor(Icon selectedItem) {
        if(selectedItem != null) {
            mName.setText(selectedItem.getName());
            mPathButton.setText(selectedItem.getPath());
        } else {
            mName.setText("");
            mPathButton.setText("Select File ...");
        }
    }

    public class IconTableCellFactory implements Callback<TableColumn<Icon, String>, TableCell<Icon, String>> {
        @Override
        public TableCell<Icon, String> call(TableColumn<Icon, String> param) {
            return new TableCell<>() {
                ImageView mImageView = new ImageView();
                @Override
                protected void updateItem(String item, boolean empty) {
                    if(item != null && !empty) {
                        Icon icon = getIconTableView().getItems().get(getIndex());
                        if(icon != null) {
                            mImageView.setImage(mIconModel.getIconImage(icon));
                            setGraphic(mImageView);
                        }
                    } else { setGraphic(null); }
                }
            };
        }
    }
}
