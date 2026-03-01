package com.solubris;

import java.util.stream.Stream;

public class StreamSugar {
    public static <T> Stream<? extends T> prepend(T t, Stream<T> tStream) {
        return Stream.concat(Stream.of(t), tStream);
    }
}
