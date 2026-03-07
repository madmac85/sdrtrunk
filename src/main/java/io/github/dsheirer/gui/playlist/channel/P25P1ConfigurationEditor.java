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

package io.github.dsheirer.gui.playlist.channel;

import io.github.dsheirer.gui.playlist.eventlog.EventLogConfigurationEditor;
import io.github.dsheirer.gui.playlist.record.RecordConfigurationEditor;
import io.github.dsheirer.gui.playlist.source.FrequencyEditor;
import io.github.dsheirer.gui.playlist.source.SourceConfigurationEditor;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.config.AuxDecodeConfiguration;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import io.github.dsheirer.module.decode.p25.phase1.Modulation;
import io.github.dsheirer.module.log.EventLogType;
import io.github.dsheirer.module.log.config.EventLogConfiguration;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.record.RecorderType;
import io.github.dsheirer.record.config.RecordConfiguration;
import io.github.dsheirer.source.config.SourceConfiguration;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P25 Phase 1 channel configuration editor
 */
public class P25P1ConfigurationEditor extends ChannelConfigurationEditor
{
    private final static Logger mLog = LoggerFactory.getLogger(P25P1ConfigurationEditor.class);
    private TitledPane mDecoderPane;
    private TitledPane mEventLogPane;
    private TitledPane mRecordPane;
    private TitledPane mSourcePane;
    private SourceConfigurationEditor mSourceConfigurationEditor;
    private EventLogConfigurationEditor mEventLogConfigurationEditor;
    private RecordConfigurationEditor mRecordConfigurationEditor;
    private ToggleSwitch mIgnoreDataCallsButton;
    private Spinner<Integer> mTrafficChannelPoolSizeSpinner;
    private Spinner<Integer> mNACSpinner;
    private Label mNACLabel;
    private SegmentedButton mModulationSegmentedButton;
    private ToggleButton mC4FMToggleButton;
    private ToggleButton mLSMToggleButton;
    private ToggleButton mLSMv2ToggleButton;

    // LSM v2 channel options
    private ToggleSwitch mIgnoreEncryptionSwitch;
    private Label mIgnoreEncryptionLabel;
    private Spinner<Integer> mMaxImbeErrorsSpinner;
    private Label mMaxImbeErrorsLabel;

    /**
     * Constructs an instance
     * @param playlistManager for playlists
     * @param tunerManager for tuners
     * @param userPreferences for preferences
     */
    public P25P1ConfigurationEditor(PlaylistManager playlistManager, TunerManager tunerManager,
                                    UserPreferences userPreferences, IFilterProcessor filterProcessor)
    {
        super(playlistManager, tunerManager, userPreferences, filterProcessor);
        getTitledPanesBox().getChildren().add(getSourcePane());
        getTitledPanesBox().getChildren().add(getDecoderPane());
        getTitledPanesBox().getChildren().add(getEventLogPane());
        getTitledPanesBox().getChildren().add(getRecordPane());
    }

    @Override
    public DecoderType getDecoderType()
    {
        return DecoderType.P25_PHASE1;
    }

    private TitledPane getSourcePane()
    {
        if(mSourcePane == null)
        {
            mSourcePane = new TitledPane("Source", getSourceConfigurationEditor());
            mSourcePane.setExpanded(true);
        }

        return mSourcePane;
    }

    private TitledPane getDecoderPane()
    {
        if(mDecoderPane == null)
        {
            mDecoderPane = new TitledPane();
            mDecoderPane.setText("Decoder: P25 Phase 1 (also used for P25 Phase 2 system with FDMA control channels)");
            mDecoderPane.setExpanded(true);

            GridPane gridPane = new GridPane();
            gridPane.setPadding(new Insets(10,10,10,10));
            gridPane.setHgap(10);
            gridPane.setVgap(10);

            Label modulationLabel = new Label("Modulation");
            GridPane.setHalignment(modulationLabel, HPos.RIGHT);
            GridPane.setConstraints(modulationLabel, 0, 0);
            gridPane.getChildren().add(modulationLabel);

            GridPane.setConstraints(getModulationSegmentedButton(), 1, 0);
            gridPane.getChildren().addAll(getModulationSegmentedButton());

            Label poolSizeLabel = new Label("Max Traffic Channels");
            GridPane.setHalignment(poolSizeLabel, HPos.RIGHT);
            GridPane.setConstraints(poolSizeLabel, 2, 0);
            gridPane.getChildren().add(poolSizeLabel);

            GridPane.setConstraints(getTrafficChannelPoolSizeSpinner(), 3, 0);
            gridPane.getChildren().add(getTrafficChannelPoolSizeSpinner());

            GridPane.setConstraints(getIgnoreDataCallsButton(), 4, 0);
            gridPane.getChildren().add(getIgnoreDataCallsButton());

            Label directionLabel = new Label("Ignore Data Calls");
            GridPane.setHalignment(directionLabel, HPos.LEFT);
            GridPane.setConstraints(directionLabel, 5, 0);
            gridPane.getChildren().add(directionLabel);

            // NAC configuration (row 1) - only visible for LSM v2
            mNACLabel = new Label("NAC (0=auto)");
            GridPane.setHalignment(mNACLabel, HPos.RIGHT);
            GridPane.setConstraints(mNACLabel, 0, 1);
            gridPane.getChildren().add(mNACLabel);

            GridPane.setConstraints(getNACSpinner(), 1, 1);
            gridPane.getChildren().add(getNACSpinner());

            Label modulationHelpLabel = new Label("C4FM: repeaters and non-simulcast trunked.  LSM: simulcast trunked.  LSM v2: conventional (PTT) CQPSK channels.");
            GridPane.setConstraints(modulationHelpLabel, 0, 2, 6, 1);
            gridPane.getChildren().add(modulationHelpLabel);

            // LSM v2 options (row 3) - only visible for LSM v2
            mIgnoreEncryptionLabel = new Label("Skip Encryption Check:");
            mIgnoreEncryptionLabel.setTooltip(new Tooltip("Start audio immediately without waiting for encryption status (for known unencrypted channels)"));
            GridPane.setHalignment(mIgnoreEncryptionLabel, HPos.RIGHT);
            GridPane.setConstraints(mIgnoreEncryptionLabel, 0, 3);
            gridPane.getChildren().add(mIgnoreEncryptionLabel);

            GridPane.setConstraints(getIgnoreEncryptionSwitch(), 1, 3);
            gridPane.getChildren().add(getIgnoreEncryptionSwitch());

            mMaxImbeErrorsLabel = new Label("Max IMBE Errors (0=off):");
            mMaxImbeErrorsLabel.setTooltip(new Tooltip("Pre-codec quality gate: frames exceeding this IMBE FEC error threshold are silenced.\n0 = disabled (default). Try 3 for simulcast channels with bimodal error patterns."));
            GridPane.setHalignment(mMaxImbeErrorsLabel, HPos.RIGHT);
            GridPane.setConstraints(mMaxImbeErrorsLabel, 2, 3, 2, 1);
            gridPane.getChildren().add(mMaxImbeErrorsLabel);

            GridPane.setConstraints(getMaxImbeErrorsSpinner(), 4, 3);
            gridPane.getChildren().add(getMaxImbeErrorsSpinner());

            // Update visibility based on modulation selection
            updateLSMv2OptionsVisibility();

            mDecoderPane.setContent(gridPane);
        }

        return mDecoderPane;
    }

    private TitledPane getEventLogPane()
    {
        if(mEventLogPane == null)
        {
            mEventLogPane = new TitledPane("Logging", getEventLogConfigurationEditor());
            mEventLogPane.setExpanded(false);
        }

        return mEventLogPane;
    }

    private TitledPane getRecordPane()
    {
        if(mRecordPane == null)
        {
            mRecordPane = new TitledPane();
            mRecordPane.setText("Recording");
            mRecordPane.setExpanded(false);

            Label notice = new Label("Note: use aliases to control call audio recording");
            notice.setPadding(new Insets(10, 10, 0, 10));

            VBox vBox = new VBox();
            vBox.getChildren().addAll(getRecordConfigurationEditor(), notice);

            mRecordPane.setContent(vBox);
        }

        return mRecordPane;
    }

    private SourceConfigurationEditor getSourceConfigurationEditor()
    {
        if(mSourceConfigurationEditor == null)
        {
            mSourceConfigurationEditor = new FrequencyEditor(mTunerManager,
                DecodeConfigP25Phase1.CHANNEL_ROTATION_DELAY_MINIMUM_MS,
                DecodeConfigP25Phase1.CHANNEL_ROTATION_DELAY_MAXIMUM_MS,
                DecodeConfigP25Phase1.CHANNEL_ROTATION_DELAY_DEFAULT_MS);

            //Add a listener so that we can push change notifications up to this editor
            mSourceConfigurationEditor.modifiedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mSourceConfigurationEditor;
    }

    private EventLogConfigurationEditor getEventLogConfigurationEditor()
    {
        if(mEventLogConfigurationEditor == null)
        {
            List<EventLogType> types = new ArrayList<>();
            types.add(EventLogType.CALL_EVENT);
            types.add(EventLogType.DECODED_MESSAGE);
            types.add(EventLogType.TRAFFIC_CALL_EVENT);
            types.add(EventLogType.TRAFFIC_DECODED_MESSAGE);

            mEventLogConfigurationEditor = new EventLogConfigurationEditor(types);
            mEventLogConfigurationEditor.setPadding(new Insets(5,5,5,5));
            mEventLogConfigurationEditor.modifiedProperty().addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mEventLogConfigurationEditor;
    }

    private SegmentedButton getModulationSegmentedButton()
    {
        if(mModulationSegmentedButton == null)
        {
            mModulationSegmentedButton = new SegmentedButton();
            mModulationSegmentedButton.getStyleClass().add(SegmentedButton.STYLE_CLASS_DARK);
            mModulationSegmentedButton.getButtons().addAll(getC4FMToggleButton(), getLSMToggleButton(), getLSMv2ToggleButton());
            mModulationSegmentedButton.getToggleGroup().selectedToggleProperty().addListener(new ChangeListener<Toggle>()
            {
                @Override
                public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue)
                {
                    if(newValue == null)
                    {
                        //Ensure at least one toggle is always selected
                        oldValue.setSelected(true);
                    }
                    else if(oldValue != null && newValue != null)
                    {
                        //Only set modified if the toggle changed from one to the other
                        modifiedProperty().set(true);
                    }
                    //Update LSM v2 options visibility based on modulation
                    updateLSMv2OptionsVisibility();
                }
            });
        }

        return mModulationSegmentedButton;
    }

    private ToggleButton getC4FMToggleButton()
    {
        if(mC4FMToggleButton == null)
        {
            mC4FMToggleButton = new ToggleButton("C4FM");
        }

        return mC4FMToggleButton;
    }

    private ToggleButton getLSMToggleButton()
    {
        if(mLSMToggleButton == null)
        {
            mLSMToggleButton = new ToggleButton("LSM");
        }

        return mLSMToggleButton;
    }

    private ToggleButton getLSMv2ToggleButton()
    {
        if(mLSMv2ToggleButton == null)
        {
            mLSMv2ToggleButton = new ToggleButton("LSM v2");
            mLSMv2ToggleButton.setTooltip(new Tooltip("LSM v2: improved cold-start for conventional (PTT) channels"));
        }

        return mLSMv2ToggleButton;
    }

    private ToggleSwitch getIgnoreDataCallsButton()
    {
        if(mIgnoreDataCallsButton == null)
        {
            mIgnoreDataCallsButton = new ToggleSwitch();
            mIgnoreDataCallsButton.setDisable(true);
            mIgnoreDataCallsButton.selectedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mIgnoreDataCallsButton;
    }

    private Spinner<Integer> getTrafficChannelPoolSizeSpinner()
    {
        if(mTrafficChannelPoolSizeSpinner == null)
        {
            mTrafficChannelPoolSizeSpinner = new Spinner();
            mTrafficChannelPoolSizeSpinner.setDisable(true);
            mTrafficChannelPoolSizeSpinner.setTooltip(
                new Tooltip("Maximum number of traffic channels that can be created by the decoder"));
            mTrafficChannelPoolSizeSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            SpinnerValueFactory<Integer> svf = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50);
            mTrafficChannelPoolSizeSpinner.setValueFactory(svf);
            mTrafficChannelPoolSizeSpinner.getValueFactory().valueProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mTrafficChannelPoolSizeSpinner;
    }

    private Spinner<Integer> getNACSpinner()
    {
        if(mNACSpinner == null)
        {
            mNACSpinner = new Spinner();
            mNACSpinner.setDisable(true);
            mNACSpinner.setTooltip(new Tooltip("Network Access Code (NAC) for this channel. Set to 0 for auto-detect, " +
                    "or enter the known NAC (1-4095) for improved decode reliability with LSM v2."));
            mNACSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            mNACSpinner.setEditable(true);
            mNACSpinner.setPrefWidth(100);
            SpinnerValueFactory<Integer> svf = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    DecodeConfigP25Phase1.NAC_MINIMUM, DecodeConfigP25Phase1.NAC_MAXIMUM, 0);
            mNACSpinner.setValueFactory(svf);
            mNACSpinner.getValueFactory().valueProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mNACSpinner;
    }

    /**
     * Updates the visibility of LSM v2 specific options based on the selected modulation.
     * NAC configuration and voice-only options are only shown for LSM v2 modulation.
     */
    private void updateLSMv2OptionsVisibility()
    {
        boolean showV2Options = getLSMv2ToggleButton().isSelected();

        // NAC spinner
        if(mNACLabel != null)
        {
            mNACLabel.setVisible(showV2Options);
            mNACLabel.setManaged(showV2Options);
        }
        getNACSpinner().setVisible(showV2Options);
        getNACSpinner().setManaged(showV2Options);

        // Skip encryption switch
        if(mIgnoreEncryptionLabel != null)
        {
            mIgnoreEncryptionLabel.setVisible(showV2Options);
            mIgnoreEncryptionLabel.setManaged(showV2Options);
        }
        getIgnoreEncryptionSwitch().setVisible(showV2Options);
        getIgnoreEncryptionSwitch().setManaged(showV2Options);

        // Max IMBE errors spinner
        if(mMaxImbeErrorsLabel != null)
        {
            mMaxImbeErrorsLabel.setVisible(showV2Options);
            mMaxImbeErrorsLabel.setManaged(showV2Options);
        }
        getMaxImbeErrorsSpinner().setVisible(showV2Options);
        getMaxImbeErrorsSpinner().setManaged(showV2Options);
    }

    private ToggleSwitch getIgnoreEncryptionSwitch()
    {
        if(mIgnoreEncryptionSwitch == null)
        {
            mIgnoreEncryptionSwitch = new ToggleSwitch();
            mIgnoreEncryptionSwitch.setDisable(true);
            mIgnoreEncryptionSwitch.selectedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }
        return mIgnoreEncryptionSwitch;
    }

    private Spinner<Integer> getMaxImbeErrorsSpinner()
    {
        if(mMaxImbeErrorsSpinner == null)
        {
            mMaxImbeErrorsSpinner = new Spinner<>();
            mMaxImbeErrorsSpinner.setDisable(true);
            mMaxImbeErrorsSpinner.setTooltip(new Tooltip(
                "Pre-codec quality gate threshold.\n" +
                "0 = disabled (default)\n" +
                "3 = recommended for some simulcast channels\n" +
                "Frames with more IMBE FEC errors than this are silenced."));
            mMaxImbeErrorsSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
            mMaxImbeErrorsSpinner.setPrefWidth(80);
            SpinnerValueFactory<Integer> svf = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                DecodeConfigP25Phase1.MAX_IMBE_ERRORS_MINIMUM, DecodeConfigP25Phase1.MAX_IMBE_ERRORS_MAXIMUM, 0);
            mMaxImbeErrorsSpinner.setValueFactory(svf);
            mMaxImbeErrorsSpinner.getValueFactory().valueProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }
        return mMaxImbeErrorsSpinner;
    }

    private RecordConfigurationEditor getRecordConfigurationEditor()
    {
        if(mRecordConfigurationEditor == null)
        {
            List<RecorderType> types = new ArrayList<>();
            types.add(RecorderType.BASEBAND);
            types.add(RecorderType.DEMODULATED_BIT_STREAM);
            types.add(RecorderType.MBE_CALL_SEQUENCE);
            types.add(RecorderType.TRAFFIC_BASEBAND);
            types.add(RecorderType.TRAFFIC_DEMODULATED_BIT_STREAM);
            types.add(RecorderType.TRAFFIC_MBE_CALL_SEQUENCE);
            mRecordConfigurationEditor = new RecordConfigurationEditor(types);
            mRecordConfigurationEditor.setDisable(true);
            mRecordConfigurationEditor.modifiedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mRecordConfigurationEditor;
    }

    @Override
    protected void setDecoderConfiguration(DecodeConfiguration config)
    {
        boolean enabled = config != null;
        getIgnoreDataCallsButton().setDisable(!enabled);
        getTrafficChannelPoolSizeSpinner().setDisable(!enabled);
        getNACSpinner().setDisable(!enabled);
        getIgnoreEncryptionSwitch().setDisable(!enabled);
        getMaxImbeErrorsSpinner().setDisable(!enabled);

        if(config instanceof DecodeConfigP25Phase1 decodeConfig)
        {
            getIgnoreDataCallsButton().setSelected(decodeConfig.getIgnoreDataCalls());
            getTrafficChannelPoolSizeSpinner().getValueFactory().setValue(decodeConfig.getTrafficChannelPoolSize());
            getNACSpinner().getValueFactory().setValue(decodeConfig.getConfiguredNAC());

            // LSM v2 options
            getIgnoreEncryptionSwitch().setSelected(decodeConfig.isIgnoreEncryptionState());
            getMaxImbeErrorsSpinner().getValueFactory().setValue(decodeConfig.getMaxImbeErrors());

            getC4FMToggleButton().setSelected(false);
            getLSMToggleButton().setSelected(false);
            getLSMv2ToggleButton().setSelected(false);
            switch(decodeConfig.getModulation())
            {
                case C4FM:
                    getC4FMToggleButton().setSelected(true);
                    break;
                case CQPSK_V2:
                    getLSMv2ToggleButton().setSelected(true);
                    break;
                default:
                    getLSMToggleButton().setSelected(true);
                    break;
            }
            updateLSMv2OptionsVisibility();
        }
        else
        {
            getIgnoreDataCallsButton().setSelected(false);
            getTrafficChannelPoolSizeSpinner().getValueFactory().setValue(0);
            getNACSpinner().getValueFactory().setValue(0);
            getIgnoreEncryptionSwitch().setSelected(false);
            getMaxImbeErrorsSpinner().getValueFactory().setValue(0);
        }
    }

    @Override
    protected void saveDecoderConfiguration()
    {
        DecodeConfigP25Phase1 config;

        if(getItem().getDecodeConfiguration() instanceof DecodeConfigP25Phase1 p1)
        {
            config = p1;
        }
        else
        {
            config = new DecodeConfigP25Phase1();
        }

        config.setIgnoreDataCalls(getIgnoreDataCallsButton().isSelected());
        config.setTrafficChannelPoolSize(getTrafficChannelPoolSizeSpinner().getValue());
        config.setConfiguredNAC(getNACSpinner().getValue());

        // LSM v2 options
        config.setIgnoreEncryptionState(getIgnoreEncryptionSwitch().isSelected());
        config.setMaxImbeErrors(getMaxImbeErrorsSpinner().getValue() != null ? getMaxImbeErrorsSpinner().getValue() : 0);

        if(getC4FMToggleButton().isSelected())
        {
            config.setModulation(Modulation.C4FM);
        }
        else if(getLSMv2ToggleButton().isSelected())
        {
            config.setModulation(Modulation.CQPSK_V2);
        }
        else
        {
            config.setModulation(Modulation.CQPSK);
        }

        getItem().setDecodeConfiguration(config);
    }

    @Override
    protected void setEventLogConfiguration(EventLogConfiguration config)
    {
        getEventLogConfigurationEditor().setItem(config);
    }

    @Override
    protected void saveEventLogConfiguration()
    {
        getEventLogConfigurationEditor().save();

        if(getEventLogConfigurationEditor().getItem().getLoggers().isEmpty())
        {
            getItem().setEventLogConfiguration(null);
        }
        else
        {
            getItem().setEventLogConfiguration(getEventLogConfigurationEditor().getItem());
        }
    }

    @Override
    protected void setAuxDecoderConfiguration(AuxDecodeConfiguration config)
    {
        //no-op
    }

    @Override
    protected void saveAuxDecoderConfiguration()
    {
        //no-op
    }

    @Override
    protected void setRecordConfiguration(RecordConfiguration config)
    {
        getRecordConfigurationEditor().setDisable(config == null);
        getRecordConfigurationEditor().setItem(config);
    }

    @Override
    protected void saveRecordConfiguration()
    {
        getRecordConfigurationEditor().save();
        RecordConfiguration config = getRecordConfigurationEditor().getItem();
        getItem().setRecordConfiguration(config);
    }

    @Override
    protected void setSourceConfiguration(SourceConfiguration config)
    {
        getSourceConfigurationEditor().setSourceConfiguration(config);
    }

    @Override
    protected void saveSourceConfiguration()
    {
        getSourceConfigurationEditor().save();
        SourceConfiguration sourceConfiguration = getSourceConfigurationEditor().getSourceConfiguration();
        getItem().setSourceConfiguration(sourceConfiguration);
    }
}
