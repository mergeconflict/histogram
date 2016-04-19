package com.mergeconflict.histogram;

/**
 * <p>An approximate histogram in constant space, based on Ben-Haim &amp; Yom-Tov,
 * "A Streaming Parallel Decision Tree Algorithm". The histogram is represented
 * as an array of contiguous bins of non-uniform width. Each bin is centered
 * on a certain point, called its "centroid," and summarizes some "count" of
 * observations. The bins are ordered in the array by their centroids; an array
 * is used rather than a linked structure for CPU cache friendliness.</p>
 *
 * <p>When the histogram is updated with a new observation, a new bin is created
 * for it, and then the pair of bins with the closest centroids are merged.
 * Since bins are stored in contiguous memory, this update process requires bins
 * to be shifted in worst-case linear time. The novel contribution of this
 * implementation is to maintain an insertion gap adjacent to the most
 * recently merged bin, such that for "well behaved" input (such as a normal
 * distribution), the number of shift operations required by an update should be
 * much less than the total number of bins on average.</p>
 */
public final class Histogram {
    private final int maxBins;
    private final double[] centroids;
    private final long[] counts;
    private int bins = 0, gap = 0;

    private long count = 0;
    private double
            min = Double.POSITIVE_INFINITY,
            max = Double.NEGATIVE_INFINITY;

    /**
     * Construct an empty histogram with a maximum number of bins.
     * @param maxBins maximum number of bins in the histogram
     */
    public Histogram(int maxBins) {
        this.maxBins = maxBins;
        this.centroids = new double[maxBins + 1];
        this.counts = new long[maxBins + 1];
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Histogram {\n");
        for (int bin = 0; bin < bins + 1; ++bin) {
            if (bin != gap) {
                sb.append("  ")
                        .append(centroids[bin])
                        .append(": ")
                        .append(counts[bin])
                        .append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Update this histogram with a new observation.
     * @param observation the new data point to be approximated in the histogram
     */
    public void update(double observation) {
        count += 1;
        if (observation < min) min = observation;
        if (observation > max) max = observation;

        // shift the insertion gap left or right to maintain ordering. if we
        // happen to find a bin whose centroid is equal to the observation,
        // just update its count in place.
        while (true) {
            // look at the bin to the left of the gap ...
            if (gap != 0) {
                if (centroids[gap - 1] > observation) {
                    // shift the gap to the left and try again.
                    centroids[gap] = centroids[gap - 1];
                    counts[gap] = counts[gap - 1];
                    gap--;
                    continue;
                } else if (centroids[gap - 1] == observation) {
                    counts[gap - 1]++;
                    return;
                }
            }

            // look at the bin to the right of the gap ...
            if (gap != bins) {
                if (centroids[gap + 1] < observation) {
                    // shift the gap to the right and try again.
                    centroids[gap] = centroids[gap + 1];
                    counts[gap] = counts[gap + 1];
                    gap++;
                    continue;
                } else if (centroids[gap + 1] == observation) {
                    counts[gap + 1]++;
                    return;
                }
            }

            // if the gap is in the right place, we're ready to insert.
            break;
        }

        // insert the observation in a new bin at the gap
        centroids[gap] = observation;
        counts[gap] = 1;

        // if the histogram isn't yet full, just stick the gap back at the end.
        if (bins != maxBins) {
            bins += 1;
            gap = bins;
            return;
        }

        // if the histogram is full, find the adjacent bins with the closest
        // centroids and merge them. the choice whether to leave the gap on the
        // left or right of the new merged bin is arbitrary.
        double minDelta = Double.POSITIVE_INFINITY;
        for (int bin = 0; bin < bins; ++bin) {
            double delta = centroids[bin + 1] - centroids[bin];
            if (delta < minDelta) {
                gap = bin;
                minDelta = delta;
            }
        }
        centroids[gap + 1] =
                (centroids[gap] * counts[gap] +
                 centroids[gap + 1] * counts[gap + 1]) /
                (counts[gap] + counts[gap + 1]);
        counts[gap + 1] = counts[gap] + counts[gap + 1];
    }

    /**
     * Query for approximate values at specified quantiles. Note that quantiles
     * must be listed in order from 0 to 1. For example:
     * <pre>{@code double[] result = histogram.query(0.00, 0.25, 0.50, 0.75, 1.00);}</pre>
     * @param quantiles an ordered array of quantiles
     * @return an array containing the approximate values at the specified
     * quantiles
     */
    public double[] query(double... quantiles) {
        double[] result = new double[quantiles.length];
        int lhs = -1;
        long lhsTotal = 0, rhsTotal = 0;

        double lhsCentroid = Double.NaN, rhsCentroid = Double.NaN;
        long lhsCount = 0, rhsCount = 0;

        // for each quantile being queried ...
        for (int q = 0; q < quantiles.length; ++q) {
            double quantile = quantiles[q];
            if (quantile <= 0) {
                result[q] = min;
                continue;
            }
            if (quantile >= 1) {
                result[q] = max;
                continue;
            }
            double needle = count * quantile;

            // find the bin containing the desired quantile
            while (rhsTotal < needle) {
                int rhs = lhs + 1;
                if (rhs == gap) rhs += 1;

                // determine the left-hand side endpoint ...
                if (lhs < 0) {
                    lhsCentroid = min;
                    lhsCount = 0;
                } else {
                    lhsCentroid = centroids[lhs];
                    lhsCount = counts[lhs];
                }

                // determine the right-hand side endpoint ...
                if (rhs > bins) {
                    rhsCentroid = max;
                    rhsCount = 0;
                } else {
                    rhsCentroid = centroids[rhs];
                    rhsCount = counts[rhs];
                }

                // determine the area of this bin, taking care to round down for
                // the left-hand side and round up for the right-hand side, such
                // that the total of all bin areas should equal the total count.
                lhsTotal = rhsTotal;
                rhsTotal += (long)
                        (Math.floor(0.5d * lhsCount) +
                         Math.ceil(0.5d * rhsCount));

                // update lhs for the next time through the loop. note that this
                // variable shouldn't be used outside the loop, only the
                // centroids and counts!
                lhs = rhs;
            }

            // approximate the value at the requested quantile ...
            double a = rhsCount - lhsCount;
            double z;
            if (a == 0) {
                double b = rhsTotal - lhsTotal;
                if (b == 0) {
                    // don't interpolate
                    z = 0;
                } else {
                    // interpolate between centroids using boring math
                    z = (needle - lhsTotal) / b;
                }
            } else {
                // interpolate between centroids using fancy math
                double b = 2 * lhsCount;
                double c = 2 * (lhsTotal - needle);
                z = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
            }
            result[q] = lhsCentroid + (rhsCentroid - lhsCentroid) * z;
        }
        return result;
    }
}
