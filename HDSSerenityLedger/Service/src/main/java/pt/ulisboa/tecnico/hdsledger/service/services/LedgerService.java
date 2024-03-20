package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;
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

    private final NodeService nodeService;
    private final ProcessLogger logger;
    private final ClientProcessConfig[] clientsConfig; // All clients configuration

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;
    private final int accumulationThreshold = 1;
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
            if (!request.verifySignature(clientsConfig)) return;

            LedgerTransferRequest transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

            // Accumulate messages
            accumulateOrPropose(request);

            // Send the response
            LedgerResponse response = LedgerResponse.builder()
                    .senderId(nodeService.getConfig().getId())
                    .type(Message.Type.TRANSFER_RESPONSE)
                    .originalRequestId(request.getLedgerRequest().getRequestId())
                    .message(MessageFormat.format("Received transfer request. Will try to transfer the amount of {0} HDS² from {1} to {2}",
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
            if (!request.verifySignature(clientsConfig)) return;

            String accountId = ((LedgerCheckBalanceRequest) request.getLedgerRequest()).getAccountId();

            Account account = nodeService.getLedger().getAccount(accountId);
            long balance = account.getBalance();

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
            e.printStackTrace();
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

    private void accumulateOrPropose(SignedLedgerRequest signedLedgerRequest) {
        accumulatedMessages.add(signedLedgerRequest);
        if (accumulatedMessages.size() >= accumulationThreshold) {
            var block = new Block();
            for (var message : accumulatedMessages) {
                block.addRequest(message);
            }

            var proposed = nodeService.startConsensus(block);

            if (proposed) {
                accumulatedMessages.clear();
            }
        }
    }
}
