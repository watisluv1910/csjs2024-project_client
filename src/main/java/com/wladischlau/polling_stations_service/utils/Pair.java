package com.wladischlau.polling_stations_service.utils;

public record Pair<T, S>(T first, S second) {

    public String toString() {
        return "(" + first + "," + second + ")";
    }
}
