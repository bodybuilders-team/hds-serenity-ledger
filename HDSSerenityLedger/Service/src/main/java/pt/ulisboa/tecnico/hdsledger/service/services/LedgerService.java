package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.models.Account;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Service used to interact with the HDSLedger.
 */
public class LedgerService implements UDPService {

    private static final int ACCUMULATION_THRESHOLD = 1;

    private final NodeService nodeService;
    private final ProcessLogger logger;
    private final ClientProcessConfig[] clientsConfig; // All clients configuration

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;
    private final List<SignedLedgerRequest> accumulatedMessages = new ArrayList<>();

    public LedgerService(
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService,
            ClientProcessConfig[] clientsConfig
    ) {
        this.nodeService = nodeService;
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.logger = new ProcessLogger(LedgerService.class.getName(), nodeService.getConfig().getId());
        this.clientsConfig = clientsConfig;
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
                    .type(Message.Type.TRANSFER_RESPONSE)
                    .originalRequestId(request.getLedgerRequest().getRequestId())
                    .message(MessageFormat.format("Received transfer request. Will try to transfer the amount of {0} HDC from {1} to {2}",
                            transferRequest.getAmount(),
                            transferRequest.getSourceAccountId(),
                            transferRequest.getDestinationAccountId()))
                    .build();

            authenticatedPerfectLink.send(request.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error transferring: {0}", e.getMessage()));
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

            String accountId = ((LedgerCheckBalanceRequest) request.getLedgerRequest()).getAccountId();

            Account account = nodeService.getLedger().getAccount(accountId);
            double balance = account.getBalance();

            logger.info(MessageFormat.format("Sending balance response: {0}", balance));

            final LedgerResponse response = LedgerResponse.builder()
                    .senderId(nodeService.getConfig().getId())
                    .originalRequestId(request.getLedgerRequest().getRequestId())
                    .type(Message.Type.BALANCE_RESPONSE)
                    .message(String.valueOf(balance))
                    .build();

            authenticatedPerfectLink.send(request.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error retrieving balance: {0}", e.getMessage()));
        }
    }

    /**
     * Accumulate messages and propose a block if the threshold is reached.
     *
     * @param signedLedgerRequest the signed ledger request
     */
    private void accumulateOrPropose(SignedLedgerRequest signedLedgerRequest) {
        accumulatedMessages.add(signedLedgerRequest);
        if (accumulatedMessages.size() >= ACCUMULATION_THRESHOLD) {
            var block = new Block();
            for (var message : accumulatedMessages)
                block.addRequest(message);

            block.setCreatorId(nodeService.getConfig().getId());

            var proposed = nodeService.startConsensus(block);

            if (proposed)
                accumulatedMessages.clear();
        }
    }


    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    final var signedMessage = authenticatedPerfectLink.receive();

                    if (!(signedMessage.getMessage() instanceof SignedLedgerRequest ledgerRequest)) {
                        continue;
                    }

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
                        }
                    }).start();

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }
}
