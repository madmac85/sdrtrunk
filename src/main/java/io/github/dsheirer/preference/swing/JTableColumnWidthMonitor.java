/*
 * ******************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2019 Dennis Sheirer
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
 * *****************************************************************************
 */

package io.github.dsheirer.preference.swing;

import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.EventQueue;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors a JTable column model and persists column width and order changes to the user preferences.
 * Restores previous column widths and positions on application restart.
 */
public class JTableColumnWidthMonitor
{
    private static final Logger mLog = LoggerFactory.getLogger(JTableColumnWidthMonitor.class);
    private UserPreferences mUserPreferences;
    private JTable mTable;
    private String mKey;
    private ColumnModelListener mColumnModelListener = new ColumnModelListener();
    private AtomicBoolean mSaveInProgress = new AtomicBoolean();
    private boolean mRestoring = false;

    /**
     * Constructs a column width monitor.
     *
     * @param userPreferences to store column widths
     * @param table to monitor for column width changes
     * @param key that uniquely identifies the table to monitor
     */
    public JTableColumnWidthMonitor(UserPreferences userPreferences, JTable table, String key)
    {
        mUserPreferences = userPreferences;
        mTable = table;
        mKey = key;

        mTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // Wait until the UI is realized to restore column widths and order
        EventQueue.invokeLater(this::restoreColumnState);

        // Listen for drag-resizes and column moves
        mTable.getColumnModel().addColumnModelListener(mColumnModelListener);
    }

    /**
     * Prepares this monitor for disposal by unregistering as a listener to the table column model.
     */
    public void dispose()
    {
        if(mTable != null && mColumnModelListener != null)
        {
            mTable.getColumnModel().removeColumnModelListener(mColumnModelListener);
        }

        mTable = null;
        mUserPreferences = null;
    }

    /**
     * Restores column widths and order from persisted settings
     */
    private void restoreColumnState()
    {
        mRestoring = true;

        try
        {
            TableColumnModel model = mTable.getColumnModel();
            int columnCount = model.getColumnCount();

            // Restore column order first
            restoreColumnOrder(model, columnCount);

            // Then restore column widths
            for(int x = 0; x < columnCount; x++)
            {
                int width = mUserPreferences.getSwingPreference().getInt(getWidthKey(x), Integer.MAX_VALUE);

                if(width != Integer.MAX_VALUE)
                {
                    model.getColumn(x).setPreferredWidth(width);
                }
            }
        }
        finally
        {
            mRestoring = false;
        }
    }

    /**
     * Restores column order from persisted model index positions.
     * Each view position stores the model index of the column that should be at that position.
     */
    private void restoreColumnOrder(TableColumnModel model, int columnCount)
    {
        // Check if we have saved order data
        int firstOrder = mUserPreferences.getSwingPreference().getInt(getOrderKey(0), Integer.MAX_VALUE);
        if(firstOrder == Integer.MAX_VALUE)
        {
            return; // No saved order
        }

        // Read saved model indices for each view position
        int[] savedOrder = new int[columnCount];
        for(int i = 0; i < columnCount; i++)
        {
            savedOrder[i] = mUserPreferences.getSwingPreference().getInt(getOrderKey(i), i);
        }

        // Move columns to their saved positions
        for(int targetViewIndex = 0; targetViewIndex < columnCount; targetViewIndex++)
        {
            int targetModelIndex = savedOrder[targetViewIndex];

            // Find the column with this model index in the current view
            int currentViewIndex = -1;
            for(int j = targetViewIndex; j < columnCount; j++)
            {
                if(model.getColumn(j).getModelIndex() == targetModelIndex)
                {
                    currentViewIndex = j;
                    break;
                }
            }

            if(currentViewIndex >= 0 && currentViewIndex != targetViewIndex)
            {
                model.moveColumn(currentViewIndex, targetViewIndex);
            }
        }
    }

    /**
     * Stores the current column widths and order to the user preferences
     */
    private void storeColumnState()
    {
        TableColumnModel model = mTable.getColumnModel();

        for(int x = 0; x < model.getColumnCount(); x++)
        {
            TableColumn column = model.getColumn(x);
            mUserPreferences.getSwingPreference().setInt(getWidthKey(x), column.getWidth());
            mUserPreferences.getSwingPreference().setInt(getOrderKey(x), column.getModelIndex());
        }
    }

    /**
     * Constructs a preference key for column width at a view position
     */
    private String getWidthKey(int column)
    {
        return mKey + ".column." + column;
    }

    /**
     * Constructs a preference key for column order (model index at a view position)
     */
    private String getOrderKey(int viewPosition)
    {
        return mKey + ".order." + viewPosition;
    }

    /**
     * Table column model listener that tracks both resize and move events.
     */
    class ColumnModelListener implements TableColumnModelListener
    {
        @Override
        public void columnMarginChanged(ChangeEvent e)
        {
            scheduleSave();
        }

        @Override
        public void columnMoved(TableColumnModelEvent e)
        {
            // Only save when the column actually moved to a new position (not during drag)
            if(!mRestoring && e.getFromIndex() != e.getToIndex())
            {
                scheduleSave();
            }
        }

        @Override
        public void columnAdded(TableColumnModelEvent e){}
        @Override
        public void columnRemoved(TableColumnModelEvent e){}
        @Override
        public void columnSelectionChanged(ListSelectionEvent e){}

        private void scheduleSave()
        {
            if(mSaveInProgress.compareAndSet(false, true))
            {
                ThreadPool.SCHEDULED.schedule(new ColumnStateSaveTask(), 2, TimeUnit.SECONDS);
            }
        }
    }

    public class ColumnStateSaveTask implements Runnable
    {
        @Override
        public void run()
        {
            storeColumnState();
            mSaveInProgress.set(false);
        }
    }
}
