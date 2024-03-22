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

    private final Queue<SignedLedgerRequest> accumulatedMessages = new LinkedList<>();

    private static final int TRANSACTION_THRESHOLD = 10;
    private static final int DELAY = 2000;
    MultiThreadTimer timer = new MultiThreadTimer();
    private final NodeProcessConfig config;
    private boolean timerElapsed = false;

    public MessageAccumulator(NodeProcessConfig config) {
        this.config = config;
    }

    public void accumulate(SignedLedgerRequest request) {
        accumulatedMessages.add(request);
    }

    public synchronized Optional<Block> getBlock(Consumer<Block> onTimerElapsed) {
        if (timerElapsed && accumulatedMessages.size() < TRANSACTION_THRESHOLD)
            return Optional.empty();

        timer.startTimer(new TimerTask() {
            @Override
            public void run() {
                var block = getBlock();

                block.ifPresent(onTimerElapsed);
            }
        }, DELAY);

        return getBlock();
    }

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
