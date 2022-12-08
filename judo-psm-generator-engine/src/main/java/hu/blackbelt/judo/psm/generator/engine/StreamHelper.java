package hu.blackbelt.judo.psm.generator.engine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class StreamHelper {
    public static <T> CompletableFuture<List<T>> allFuture(List<CompletableFuture<T>> futures) {
        CompletableFuture[] cfs = futures.toArray(new CompletableFuture[futures.size()]);

        return CompletableFuture.allOf(cfs)
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }

    public static <T> List<T> performFutures(List<CompletableFuture<T>> futures) throws ExecutionException, InterruptedException {
        return allFuture(futures).get();
    }

}
