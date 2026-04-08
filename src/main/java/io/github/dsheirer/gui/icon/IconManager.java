package io.github.dsheirer.gui.icon;

import io.github.dsheirer.gui.playlist.Editor;
import io.github.dsheirer.icon.Icon;
import io.github.dsheirer.icon.IconModel;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IconManager extends Editor<Icon>
{
    private final static Logger mLog = LoggerFactory.getLogger(IconManager.class);
    private TableView<Icon> mIconTableView = new TableView<>();
    private IconModel mIconModel;

    public IconManager(IconModel iconModel)
    {
        mIconModel = iconModel;
        
        VBox layout = new VBox();
        
        try {
            mIconTableView.setItems(new SortedList<>(mIconModel.iconsProperty()));
        } catch (Exception e) {
            mLog.error("Could not bind icons property");
        }

        layout.getChildren().add(mIconTableView);
        
        // Use setEditorNode which is the standard method in this version of SDRTrunk
        setEditorNode(layout);
    }

    @Override
    public void save() {
        // Required by the Editor base class
    }

    public void cancel() {
        // Optional implementation
    }

    public void dispose() {
        // Optional implementation
    }

    @Override
    protected TableView<Icon> getTableView() {
        return mIconTableView;
    }
}
