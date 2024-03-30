package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.service.MessageAccumulator;
import pt.ulisboa.tecnico.hdsledger.shared.MultiThreadTimer;
import pt.ulisboa.tecnico.hdsledger.shared.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;

import java.text.MessageFormat;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service used to interact with the HDSLedger.
 */
public class LedgerService implements UDPService {

    private static final int DELAY = 2000;
    final MultiThreadTimer timer = new MultiThreadTimer();
    private final NodeService nodeService;
    private final ProcessLogger logger;
    private final ClientProcessConfig[] clientsConfig; // All clients configuration
    private final MessageAccumulator messageAccum;
    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;
    private final AtomicBoolean previousConsensusStartFinished = new AtomicBoolean(true);


    public LedgerService(
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService,
            ClientProcessConfig[] clientsConfig,
            MessageAccumulator messageAccum
    ) {
        this.nodeService = nodeService;
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.logger = new ProcessLogger(LedgerService.class.getName(), nodeService.getConfig().getId());
        this.clientsConfig = clientsConfig;
        this.messageAccum = messageAccum;
    }


    /**
     * Handles a transfer request.
     *
     * @param request the transfer request
     */
    public void uponTransfer(SignedLedgerRequest request) {
        logger.info(MessageFormat.format("Received transfer request: {0}", request));

        try {
            boolean validTransfer = request.verifySignature(clientsConfig);
            if (!validTransfer)
                logger.warn("Failed to transfer: signature of the request is not from the source account.");

            LedgerTransferRequest transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

            // Accumulate messages
            accumulateOrPropose(request);

            // Send the response
            LedgerResponse response = LedgerResponse.builder()
                    .senderId(nodeService.getConfig().getId())
                    .type(Message.Type.LEDGER_ACK)
                    .originalRequestId(request.getLedgerRequest().getRequestId())
                    .message(MessageFormat.format("Received transfer request. Will try to transfer the amount of {0} HDC from {1} to {2}",
                            transferRequest.getAmount(),
                            transferRequest.getSourceAccountId(),
                            transferRequest.getDestinationAccountId()))
                    .build();

            authenticatedPerfectLink.send(request.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error transferring: {0}", e.getMessage()));
            e.printStackTrace();
        }
    }


    /**
     * Handles a balance request.
     *
     * @param request the balance request
     */
    public void uponBalance(SignedLedgerRequest request) {
        logger.info(MessageFormat.format("Received balance request: {0}", request));

        try {
            if (!request.verifySignature(clientsConfig)) {
                logger.warn("Failed to check balance: signature of the request is not from the requester.");
                return;
            }

            LedgerCheckBalanceRequest ledgerRequest = (LedgerCheckBalanceRequest) request.getLedgerRequest();

            // Accumulate messages
            accumulateOrPropose(request);

            // Send the response
            LedgerResponse response = LedgerResponse.builder()
                    .senderId(nodeService.getConfig().getId())
                    .type(Message.Type.LEDGER_ACK)
                    .originalRequestId(ledgerRequest.getRequestId())
                    .message(MessageFormat.format("Received balance request. Will try to check the balance of account {0}",
                            ledgerRequest.getAccountId()))
                    .build();

            authenticatedPerfectLink.send(request.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error retrieving balance: {0}", e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * Accumulate messages and propose a block if the threshold is reached.
     *
     * @param signedLedgerRequest the signed ledger request
     */
    private void accumulateOrPropose(SignedLedgerRequest signedLedgerRequest) {
        if (!nodeService.getLedger().validateRequest(signedLedgerRequest)) {
            logger.warn("Failed to validate request. Not accumulating.");
            return;
        }

        if (this.nodeService.getConfig().getBehavior() == ProcessConfig.ProcessBehavior.BULLY_LEADER
                && signedLedgerRequest.getSenderId().equals("101"))
            return;

        messageAccum.accumulate(signedLedgerRequest);

        checkConsensus();
    }

    /**
     * Checks if there are enough requests to start consensus.
     * If there are, and the previous consensus has finished, start a new consensus.
     * Otherwise, start a timer to check again.
     */
    private void checkConsensus() {
        logger.debug("Checking consensus...");
        boolean startConsensus = false;
        synchronized (previousConsensusStartFinished) {
            if (previousConsensusStartFinished.get() && messageAccum.enoughRequests()) {
                previousConsensusStartFinished.set(false);
                startConsensus = true;
            }
        }

        if (startConsensus) {
            nodeService.startConsensus(messageAccum::getBlock);
            previousConsensusStartFinished.set(true);
        } else
            timer.startTimer(new TimerTask() {
                @Override
                public void run() {
                    logger.debug("Timer elapsed. Checking consensus...");
                    checkConsensusWithoutEnoughRequests();
                }
            }, DELAY);
    }

    /**
     * Checks if there are enough requests to start consensus.
     * If there are, and the previous consensus has finished, start a new consensus.
     * Otherwise, wait for the previous consensus to finish.
     */
    private void checkConsensusWithoutEnoughRequests() {
        if (previousConsensusStartFinished.get()) {
            nodeService.startConsensus(messageAccum::getBlock);
        } else {
            try {
                Thread.sleep(100); //TODO: Change to wait-notify
                checkConsensusWithoutEnoughRequests();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    final var signedMessage = authenticatedPerfectLink.receive();

                    if (!(signedMessage.getMessage() instanceof SignedLedgerRequest ledgerRequest))
                        continue;

                    new Thread(() -> {
                        try {
                            switch (ledgerRequest.getType()) {
                                case BALANCE -> uponBalance(ledgerRequest);
                                case TRANSFER -> uponTransfer(ledgerRequest);
                                case IGNORE -> {/* Do nothing */}
                                default ->
                                        logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerRequest.getType()));
                            }
                        } catch (Exception e) {
                            logger.error(MessageFormat.format("Error processing message: {0}", e.getMessage()));
                            e.printStackTrace();
                        }
                    }).start();

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
