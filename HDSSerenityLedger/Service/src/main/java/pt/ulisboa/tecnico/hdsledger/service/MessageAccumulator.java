package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.util.Deque;
import java.util.LinkedList;

/**
 * The {@code MessageAccumulator} class represents a mempool for the ledger requests.
 * It accumulates requests until a threshold is reached or a timer expires.
 */
public class MessageAccumulator {

    private static final int TRANSACTION_THRESHOLD = 3;
    private final Deque<SignedLedgerRequest> accumulatedMessages = new LinkedList<>();
    private final NodeProcessConfig config;

    public MessageAccumulator(NodeProcessConfig config) {
        this.config = config;
    }

    /**
     * Accumulates a request.
     *
     * @param request the request to accumulate
     */
    public void accumulate(SignedLedgerRequest request) {
        accumulatedMessages.add(request);
    }

    public boolean enoughRequests() {
        return accumulatedMessages.size() >= TRANSACTION_THRESHOLD;
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

    /**
     * Gets a block of requests.
     *
     * @return an optional block of requests
     */
    public synchronized Block getBlock() {
        var block = new Block();

        for (int i = 0; i < TRANSACTION_THRESHOLD; i++) {
            var request = accumulatedMessages.poll();
            if (request == null)
                break;

            block.addRequest(request);
        }

        block.setCreatorId(config.getId());


        return block;
    }

    /**
     * Removes a request from the accumulator.
     *
     * @param request the request to remove
     */
    public void remove(SignedLedgerRequest request) {
        accumulatedMessages.remove(request);
    }

}
