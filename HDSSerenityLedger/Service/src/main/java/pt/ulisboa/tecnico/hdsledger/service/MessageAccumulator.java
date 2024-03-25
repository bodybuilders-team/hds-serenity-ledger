package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.shared.MultiThreadTimer;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.TimerTask;
import java.util.function.Consumer;

public class MessageAccumulator {

    private static final int TRANSACTION_THRESHOLD = 10;
    private static final int DELAY = 2000;
    final MultiThreadTimer timer = new MultiThreadTimer();
    private final Queue<SignedLedgerRequest> accumulatedMessages = new LinkedList<>();
    private final NodeProcessConfig config;
    private boolean timerElapsed = true;

    public MessageAccumulator(NodeProcessConfig config) {
        this.config = config;
    }

    public void accumulate(SignedLedgerRequest request) {
        accumulatedMessages.add(request);
    }

    public synchronized Optional<Block> getBlock(Consumer<Block> onTimerElapsed) {
        timer.startTimer(new TimerTask() {
            @Override
            public void run() {
                var block = getBlock();

                block.ifPresent(onTimerElapsed);
            }
        }, DELAY);

        if (accumulatedMessages.size() < TRANSACTION_THRESHOLD)
            return Optional.empty();

        timer.stopTimer();

        return getBlock();
    }

    /*
    To start a timer and not restart it if it is already running. The way we still have above makes it
    so that the timer is restarted upon a new request being added to the accumulator.
    Is this the expected behavior?
    Each new request may come faster than the DELAY, restarting the timer, until the THRESHOLD is reached.
    (this basically means that DELAY is equal to the total max time tolerated to be waited divided by the threshold)
    DELAY = maxTime / THRESHOLD

    ---------------------------------------------------------------------
    The code below makes it so that the timer is only started once, and any new requests that come before it expires
    are still added, but any others that come after are not included in the same block.

    public synchronized Optional<Block> getBlock(Consumer<Block> onTimerElapsed) {
        synchronized (timer) {
            if (timerElapsed) {
                timerElapsed = false;
                timer.startTimer(new TimerTask() {
                    @Override
                    public void run() {
                        var block = getBlock();

                        block.ifPresent(onTimerElapsed);
                        synchronized (timer) {
                            timerElapsed = true;
                        }
                    }
                }, DELAY);
            }
        }

        if (accumulatedMessages.size() < TRANSACTION_THRESHOLD)
            return Optional.empty();

        timer.stopTimer();
        timerElapsed = true;

        return getBlock();
    }
    * */

    public synchronized Optional<Block> getBlock() {
        var block = new Block();

        for (int i = 0; i < TRANSACTION_THRESHOLD; i++) {
            var request = accumulatedMessages.poll();
            if (request == null)
                break;

            block.addRequest(request);
        }

        if (block.getRequests().isEmpty())
            return Optional.empty();

        accumulatedMessages.clear();

        block.setCreatorId(config.getId());

        return Optional.of(block);
    }

    public void remove(SignedLedgerRequest request) {
        accumulatedMessages.remove(request);
    }
}
