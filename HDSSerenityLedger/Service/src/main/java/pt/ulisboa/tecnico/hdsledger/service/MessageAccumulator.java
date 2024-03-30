package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code MessageAccumulator} class represents a mempool for the ledger requests.
 * It accumulates requests until a threshold is reached or a timer expires.
 */
public class MessageAccumulator {

    private static final int TRANSACTION_THRESHOLD = 3;
    private final List<SignedLedgerRequest> accumulatedMessages = new ArrayList<>();
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

    /**
     * Checks if there are enough requests to create a block.
     *
     * @return true if there are enough requests, false otherwise
     */
    public boolean enoughRequests() {
        return accumulatedMessages.size() >= TRANSACTION_THRESHOLD;
    }

    /**
     * Gets a block of requests.
     *
     * @return an optional block of requests
     */
    public synchronized Block getBlock() {
        var block = new Block();

        for (int i = 0; i < Math.min(TRANSACTION_THRESHOLD, accumulatedMessages.size()); i++) {
            var request = accumulatedMessages.get(i);
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
