package com.mergeconflict.histogram;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) {
        Histogram h = new Histogram(1000);
        Set<Double> actualSet = new TreeSet<>();
        for (int i = 0; i < 1000000; ++i) {
            double d = ThreadLocalRandom.current().nextGaussian();
            h.update(d);
            actualSet.add(d);
        }
        Double[] actual = new Double[1000000];
        actualSet.toArray(actual);
        System.out.println(Arrays.toString(h.query(0, .002, .023, .159, .500, .841, .977, .998, 1)));
        System.out.format("[%s, %s, %s, %s, %s, %s, %s, %s, %s]\n",
                actual[0],
                actual[2000],
                actual[23000],
                actual[159000],
                actual[500000],
                actual[841000],
                actual[977000],
                actual[998000],
                actual[999999]
                );
    }
}
