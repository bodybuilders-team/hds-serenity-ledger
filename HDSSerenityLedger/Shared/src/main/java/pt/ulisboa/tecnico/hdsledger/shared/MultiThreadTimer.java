package pt.ulisboa.tecnico.hdsledger.shared;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Multi-thread timer to handle multiple timers at the same time.
 */
public class MultiThreadTimer {
    private Timer timer;

    public MultiThreadTimer() {
        this.timer = new Timer();
    }

    /**
     * Start the timer with a task and a delay.
     * If the timer is already running, it is stopped and a new one is created.
     *
     * @param task  the task to run
     * @param delay the delay in milliseconds
     */
    public void startTimer(TimerTask task, long delay) {
        synchronized (this) {
            this.stopTimer();
            this.timer.schedule(task, delay);
        }
    }

    /**
     * Stop the timer and create a new one.
     */
    public void stopTimer() { // TODO: Rename to resetTimer? stopAndResetTimer? stopTimer is misleading for me because is not mentioned that a new timer is created
        synchronized (this) {
            this.timer.cancel();
            this.timer = new Timer();
        }
    }
}
