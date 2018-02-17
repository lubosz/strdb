package eu.metatools.dbs;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

import static eu.metatools.dbs.Config.prefixLength;


public class Tests {
    static class Benchmark {
        private long startTime;
        private long endTime;

        Benchmark() {
            startTime = System.nanoTime();
        }

        void end() {
            endTime = System.nanoTime();
        }

        Double duration() {
            return nsToMs(endTime - startTime);
        }

        static double nsToMs(double ns) {
            return ns / (1000.0 * 1000.0);
        }
    }

    private final static String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static String randomString(int length) {
        StringBuilder result = new StringBuilder();
        Random rnd = new Random();
        while (result.length() < length) {
            int index = (int) (rnd.nextFloat() * alphabet.length());
            result.append(alphabet.charAt(index));
        }
        return result.toString();

    }

    private static LinkedList<String> genRandomWords(int iterations, int length) {
        final LinkedList<String> randomWords = new LinkedList<>();

        for (int i = 0; i < iterations; i++)
            randomWords.add(randomString(length));

        return randomWords;
    }

    private static LinkedList<String> queryDistributed(StrDb strDb, final int iterations) throws IOException {
        final LinkedList<String> distributedWords = new LinkedList<>();
        final int wordCount = strDb.getCount();
        final int step = wordCount / iterations;

        strDb.words(new Consumer<String>() {
            int current = 0;
            @Override
            public void apply(String item) {

                if (current < wordCount && current % step == 0)
                    distributedWords.add(item);

                current++;
            }
        });

        return distributedWords;
    }

    private static LinkedList<String> genSameWords(int iterations, String word) {
        final LinkedList<String> randomWords = new LinkedList<>();

        for (int i = 0; i < iterations; i++)
            randomWords.add(word);

        return randomWords;
    }

    private static double average(LinkedList<Double> durations) {
        Double sum = 0.0;
        if (durations.isEmpty())
            return sum;

        for (Double duration : durations)
            sum += duration;

        return sum / durations.size();
    }

    private static void printDurationStats(LinkedList<Double> durations) {
        System.out.println("\n--------------------\n");

        System.out.format("First %.4fms\n", durations.getFirst());

        Collections.sort(durations);

        System.out.println();

        System.out.format("Best %.4fms\n", durations.getFirst());
        System.out.format("Worst %.4fms\n", durations.getLast());

        System.out.println();

        System.out.format("Median %.4fms\n", durations.get((int)(durations.size()/2.0)));
        System.out.format("Average %.4fms\n", average(durations));

        System.out.println("\n----- CUT HERE -----\n");
    }


    private static void benchmarkWords(StrDb strDb, LinkedList<String> words) throws  IOException {
        LinkedList<Double> durations = new LinkedList<>();
        for (String word : words) {

            if (word.length() < prefixLength) {
                System.out.format("%s is shorter than prefix. Unsupported.\n", word);
                continue;
            }

            Benchmark b = new Benchmark();
            boolean found = strDb.contains(word);
            b.end();
            Double d = b.duration();
            System.out.format("%b %.4fms %s\n", found, d, word);
            durations.add(d);
        }

        printDurationStats(durations);
    }

    private static void benchmarkFromDict(StrDb strDb, int iterations) throws IOException {
        LinkedList<String> distributedWords = queryDistributed(strDb, iterations);
        benchmarkWords(strDb, distributedWords);
    }

    private static void benchmarkRandom(StrDb strDb, final int iterations) throws IOException {
        final LinkedList<String> randomWords = genRandomWords(iterations, 10);
        Collections.sort(randomWords);
        benchmarkWords(strDb, randomWords);
    }

    private static void benchmarkSameWord(StrDb strDb, String word, int iterations) throws IOException {
        if (word.length() < prefixLength) {
            System.out.format("%s is shorter than prefix. Unsupported.\n", word);
            return;
        }

        LinkedList<String> sameWords = genSameWords(iterations, word);
        benchmarkWords(strDb, sameWords);
    }

    static void someTests(StrDb strDb) throws IOException {
        System.out.println(strDb.contains("precip"));
        System.out.println(strDb.resolve("prenewtonian"));
        strDb.words(new Consumer<String>() {
            @Override
            public void apply(String item) {
                if (item.contains("oo"))
                    System.out.println(item);
            }
        });
    }

    public static void main(String[] args) throws IOException {

        final StrDb strDb = new StrDb(
                Files.asByteSource(new File("english_words_all.zip")),
                "english_words_all/",
                prefixLength,
                StandardCharsets.UTF_8);

        // someTests(strDb);

        benchmarkRandom(strDb, 20);
        benchmarkFromDict(strDb, 50);

        // Test with first and last word
        final int wordCount = strDb.getCount();
        strDb.words(new Consumer<String>() {
            int current = 0;
            @Override
            public void apply(String item) {
                current++;
                if (current == wordCount || current == 1)
                    try {
                        benchmarkSameWord(strDb, item, 9);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        });
    }
}
