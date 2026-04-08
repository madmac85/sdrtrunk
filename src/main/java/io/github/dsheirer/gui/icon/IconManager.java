package io.github.dsheirer.gui.icon;

import io.github.dsheirer.gui.playlist.Editor;
import io.github.dsheirer.icon.Icon;
import io.github.dsheirer.icon.IconModel;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

public class IconManager extends Editor<Icon> {
    private TableView<Icon> mIconTableView = new TableView<>();
    private IconModel mIconModel;

    public IconManager(IconModel iconModel) {
        super(); 
        this.mIconModel = iconModel;
        
        VBox layout = new VBox();
        mIconTableView.setItems(mIconModel.getIcons());
        
        TableColumn<Icon, String> iconColumn = new TableColumn<>("Icon");
        iconColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        iconColumn.setCellFactory(new IconTableCellFactory());
        
        TableColumn<Icon, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        mIconTableView.getColumns().addAll(iconColumn, nameColumn);
        layout.getChildren().add(mIconTableView);
    }

    @Override
    public void save() { }

    @Override
    public void dispose() { }

    protected TableView<Icon> getTableView() {
        return mIconTableView;
    }

    public class IconTableCellFactory implements Callback<TableColumn<Icon, String>, TableCell<Icon, String>> {
        @Override
        public TableCell<Icon, String> call(TableColumn<Icon, String> param) {
            return new TableCell<>() {
                private final ImageView mImageView = new ImageView();
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && !empty) {
                        Icon icon = getTableView().getItems().get(getIndex());
                        // This calls the model to load the actual SVG/PNG image
                        mImageView.setImage(mIconModel.getIconImage(icon));
                        setGraphic(mImageView);
                    } else {
                        setGraphic(null);
                    }
                }
            };
        }
    }
}
