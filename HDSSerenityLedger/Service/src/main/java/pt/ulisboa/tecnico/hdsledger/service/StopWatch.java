package pt.ulisboa.tecnico.hdsledger.service;

import lombok.Getter;

public class StopWatch {
    private long startTime;
    @Getter
    private boolean running;

    public StopWatch() {
        startTime = 0;
        running = false;
    }

    public void start() {
        startTime = System.currentTimeMillis();
        running = true;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

}
