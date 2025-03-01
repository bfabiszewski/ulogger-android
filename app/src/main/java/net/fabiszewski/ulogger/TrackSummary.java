/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

/**
 * Track summary
 *
 */

public class TrackSummary {
    /**
     * Track distance in meters
     */
    final private long distance;
    /**
     * Track duration in seconds
     */
    final private long duration;
    /**
     * Count of track positions
     */
    final private long positionsCount;

    /**
     * Constructor
     * @param mDistance Distance (meters)
     * @param mDuration Duration (seconds)
     * @param mPositionsCount Number of positions
     */
    public TrackSummary(long mDistance, long mDuration, long mPositionsCount) {
        distance = mDistance;
        duration = mDuration;
        positionsCount = mPositionsCount;
    }

    /**
     * Get track distance
     * @return Distance in meters
     */
    public long getDistance() {
        return distance;
    }

    /**
     * Get track duration
     * @return Duration in seconds
     */
    public long getDuration() { return duration; }

    /**
     * Get count of positions
     * @return Count
     */
    public long getPositionsCount() {
        return positionsCount;
    }
}
