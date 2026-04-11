package io.github.dsheirer.module.decode.config;

public class TwoToneDetector {
    private TwoToneDetectorConfiguration mConfiguration;

    public TwoToneDetector(TwoToneDetectorConfiguration configuration) {
        mConfiguration = configuration;
    }

    public TwoToneDetectorConfiguration getConfiguration() {
        return mConfiguration;
    }
}
