package com.airijko.endlessleveling.xpstats;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-profile XP statistics data: lifetime total, hourly/daily rolling buckets,
 * and prestige history. Fixed-size arrays give O(1) updates and bounded reads.
 */
public class XpStatsData {

    private double totalXp;
    private final double[] hourly = new double[24];
    private final double[] daily = new double[7];
    private int lastHour;
    private int lastDay;
    private double lastTotalXp;
    private final List<PrestigeEvent> prestigeHistory = new ArrayList<>();

    private transient boolean dirty;

    public XpStatsData() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        this.lastHour = now.getHour();
        this.lastDay = now.getDayOfWeek().getValue() - 1; // Monday=0 .. Sunday=6
    }

    // ------------------------------------------------------------------
    // Core tracking
    // ------------------------------------------------------------------

    /**
     * Records an XP gain amount into the rolling buckets and lifetime total.
     * Rotates stale buckets before recording.
     */
    public void recordXpGain(double amount) {
        if (amount <= 0) return;
        rotateBuckets();
        totalXp += amount;
        hourly[lastHour] += amount;
        daily[lastDay] += amount;
        dirty = true;
    }

    /**
     * Checks whether the current hour/day differs from the last recorded
     * indices and clears stale bucket slots.
     */
    public void rotateBuckets() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int currentHour = now.getHour();
        int currentDay = now.getDayOfWeek().getValue() - 1;

        if (currentHour != lastHour) {
            // Clear all hours between lastHour+1 and currentHour (wrapping)
            int h = (lastHour + 1) % 24;
            while (h != currentHour) {
                hourly[h] = 0.0;
                h = (h + 1) % 24;
            }
            hourly[currentHour] = 0.0;
            lastHour = currentHour;
            dirty = true;
        }

        if (currentDay != lastDay) {
            // Clear all days between lastDay+1 and currentDay (wrapping)
            int d = (lastDay + 1) % 7;
            while (d != currentDay) {
                daily[d] = 0.0;
                d = (d + 1) % 7;
            }
            daily[currentDay] = 0.0;
            lastDay = currentDay;
            dirty = true;
        }
    }

    /**
     * Applies offline catch-up: if totalXp was modified externally (e.g. admin set),
     * distributes the delta into the current bucket.
     */
    public void applyCatchUp() {
        double delta = totalXp - lastTotalXp;
        if (delta > 0) {
            rotateBuckets();
            hourly[lastHour] += delta;
            daily[lastDay] += delta;
            dirty = true;
        }
        lastTotalXp = totalXp;
    }

    public void recordPrestige(int prestigeLevel) {
        long timestamp = System.currentTimeMillis() / 1000L;
        prestigeHistory.add(new PrestigeEvent(timestamp, prestigeLevel));
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Computed values
    // ------------------------------------------------------------------

    /** Sum of all 24 hourly buckets. */
    public double getXp24h() {
        return Arrays.stream(hourly).sum();
    }

    /** Sum of all 7 daily buckets. */
    public double getXp7d() {
        return Arrays.stream(daily).sum();
    }

    /**
     * Momentum = xp24h / average of the other 6 days in the weekly window.
     * Returns 0.0 when xp24h is zero.
     * Capped at 999.0 when previous average is zero but xp24h > 0.
     */
    public double getMomentum() {
        double xp24h = getXp24h();
        if (xp24h <= 0) return 0.0;

        double todayDaily = daily[lastDay];
        double otherDaysTotal = getXp7d() - todayDaily;
        double previousAvg = otherDaysTotal / 6.0;

        if (previousAvg <= 0) return 999.0;
        return xp24h / previousAvg;
    }

    // ------------------------------------------------------------------
    // Getters / setters for serialization
    // ------------------------------------------------------------------

    public double getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(double totalXp) {
        this.totalXp = totalXp;
    }

    public double[] getHourly() {
        return hourly;
    }

    public double[] getDaily() {
        return daily;
    }

    public int getLastHour() {
        return lastHour;
    }

    public void setLastHour(int lastHour) {
        this.lastHour = lastHour;
    }

    public int getLastDay() {
        return lastDay;
    }

    public void setLastDay(int lastDay) {
        this.lastDay = lastDay;
    }

    public double getLastTotalXp() {
        return lastTotalXp;
    }

    public void setLastTotalXp(double lastTotalXp) {
        this.lastTotalXp = lastTotalXp;
    }

    public List<PrestigeEvent> getPrestigeHistory() {
        return prestigeHistory;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    // ------------------------------------------------------------------
    // Inner types
    // ------------------------------------------------------------------

    public record PrestigeEvent(long timestamp, int prestige) {
    }
}
