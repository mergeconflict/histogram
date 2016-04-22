package com.mergeconflict.histogram;

import org.junit.Test;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

public class HistogramTest {

    @Test
    public void histogramMustFitData() {
        // use the current thread's random number generator
        Random random = ThreadLocalRandom.current();

        // store exact observations to compare to the histogram approximations
        Histogram histogram = new Histogram(10);
        double[] expected = new double[1000];
        for (int i = 0; i < 1000; ++i) {
            double observation = random.nextGaussian();
            histogram.update(observation);
            expected[i] = observation;
        }
        Arrays.sort(expected);

        // query the histogram at quantiles from 0 to 1 inclusive
        double[] quantiles = new double[1000];
        Arrays.setAll(quantiles, i -> i / 999d);
        double[] actual = histogram.query(quantiles);

        // compute R squared ...
        double mean = 0;
        for (int i = 0; i < 1000; ++i) {
            mean += actual[i];
        }
        mean /= 1000;

        double residualSumOfSquares = 0, totalSumOfSquares = 0;
        for (int i = 0; i < 1000; ++i) {
            residualSumOfSquares += Math.pow(actual[i] - expected[i], 2);
            totalSumOfSquares += Math.pow(actual[i] - mean, 2);
        }
        residualSumOfSquares = Math.sqrt(residualSumOfSquares / 1000);
        totalSumOfSquares = Math.sqrt(totalSumOfSquares / 1000);
        assertEquals(1, 1 - residualSumOfSquares / totalSumOfSquares, 0.05);
    }
}
