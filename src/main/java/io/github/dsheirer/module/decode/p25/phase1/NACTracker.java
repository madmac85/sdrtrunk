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

package io.github.dsheirer.module.decode.p25.phase1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the dominant NAC value for a channel to aid in NID error detection and verification.
 */
public class NACTracker
{
    private static final Comparator<Tracker> COUNT_DESCENDING_COMPARATOR = new CountDescendingSorter();
    private static final Comparator<Tracker> TIME_ASCENDING_COMPARATOR = new TimeAscendingSorter();
    private final Map<Integer, Tracker> mTrackerMap = new HashMap<>();
    private static final int MAX_TRACKER_COUNT = 3;
    private static final int MIN_OBSERVATION_THRESHOLD = 2; // Reduced from 3 for faster NAC acquisition

    // User-configured NAC value (0 = not configured, use tracking)
    private int mConfiguredNAC = 0;

    /**
     * Sets a user-configured NAC value. When set (non-zero), this NAC will always be returned
     * by getTrackedNAC() regardless of observed NAC values, providing optimal NID error correction.
     *
     * @param nac the configured NAC value, or 0 to use automatic tracking
     */
    public void setConfiguredNAC(int nac)
    {
        mConfiguredNAC = nac;
    }

    /**
     * Gets the user-configured NAC value, or 0 if not configured.
     */
    public int getConfiguredNAC()
    {
        return mConfiguredNAC;
    }

    /**
     * Indicates if a user-configured NAC value is set.
     */
    public boolean hasConfiguredNAC()
    {
        return mConfiguredNAC > 0;
    }

    /**
     * Removes all tracked NAC values.  Invoke this method after an extended loss of sync.
     * Note: Does not clear the configured NAC value.
     */
    public void reset()
    {
        mTrackerMap.clear();
    }

    /**
     * Tracks the NAC value.  Each time the NAC value is observed, the observation count is incremented so that the
     * dominant NAC value will have the highest observation count.  Additionally, the last updated timestamp for the
     * tracker is updated to now.  When the tracker count exceeds the max tracker threshold, the tracker with the
     * oldest updated timestamp is thrown out.
     * @param nac to track
     */
    public void track(int nac)
    {
        Tracker tracker = mTrackerMap.get(nac);

        if(tracker == null)
        {
            mTrackerMap.put(nac, new Tracker(nac));

            if(mTrackerMap.size() > MAX_TRACKER_COUNT)
            {
                List<Tracker> trackerList = new ArrayList<>(mTrackerMap.values());
                trackerList.sort(TIME_ASCENDING_COMPARATOR);
                Tracker oldest = trackerList.getFirst();
                mTrackerMap.remove(oldest.nac());
            }
        }
        else
        {
            tracker.increment();
        }
    }

    /**
     * Identifies the dominant NAC value by sorting the tracked NAC values in count order with the highest count NAC
     * value identified as the dominant NAC.  The dominant NAC must have at least 3 observations to be the dominant NAC.
     *
     * If a user-configured NAC is set, returns that value instead of the tracked value.
     *
     * @return dominant tracked NAC value, or configured NAC if set.
     */
    public int getTrackedNAC()
    {
        // If user has configured a known NAC, always use it
        if(mConfiguredNAC > 0)
        {
            return mConfiguredNAC;
        }

        if(mTrackerMap.isEmpty())
        {
            return 0;
        }

        List<Tracker> trackers = new ArrayList<>(mTrackerMap.values());
        trackers.sort(COUNT_DESCENDING_COMPARATOR);
        Tracker highestCount = trackers.getFirst();

        if(highestCount.count() >= MIN_OBSERVATION_THRESHOLD)
        {
            return highestCount.nac();
        }

        return 0;
    }

    /**
     * Tracked NAC value class.
     */
    private class Tracker
    {
        private int mNAC;
        private long mTimestamp = System.currentTimeMillis();
        private int mCount = 1;

        /**
         * Constructs an instance to track the NAC.  Note: count is set to 1 and timestamp is set to now.
         * @param nac to track.
         */
        private Tracker(int nac)
        {
            mNAC = nac;
        }

        /**
         * Tracked NAC value for this tracker.
         * @return nac value.
         */
        private int nac()
        {
            return mNAC;
        }

        /**
         * Increments the observation count for this tracker and updates the timestamp.
         */
        public void increment()
        {
            mCount++;

            //Don't let the count value rollover to negative
            if(mCount < 0)
            {
                mCount = Integer.MAX_VALUE;
            }

            mTimestamp = System.currentTimeMillis();
        }

        /**
         * Last observation timestamp.
         * @return timestamp.
         */
        public long timestamp()
        {
            return mTimestamp;
        }

        /**
         * Observation count for this tracker.
         * @return count
         */
        public int count()
        {
            return mCount;
        }

        /**
         * Pretty print.
         */
        @Override
        public String toString()
        {
            return "NAC [" + mNAC + "] COUNT [" + mCount + "] " + new Date(mTimestamp);
        }
    }

    /**
     * Sorts the tracked NACs in count order where index 0 is the highest tracked value.
     */
    public static class CountDescendingSorter implements Comparator<Tracker>
    {
        @Override
        public int compare(Tracker o1, Tracker o2)
        {
            //Simply swap the order for comparison to get the reverse count sort
            return Integer.compare(o2.count(), o1.count());
        }
    }

    /**
     * Sorts the tracked NACs in time order where index 0 is the oldest tracked value.
     */
    public static class TimeAscendingSorter implements Comparator<Tracker>
    {
        @Override
        public int compare(Tracker o1, Tracker o2)
        {
            return Long.compare(o1.timestamp(), o2.timestamp());
        }
    }
}
