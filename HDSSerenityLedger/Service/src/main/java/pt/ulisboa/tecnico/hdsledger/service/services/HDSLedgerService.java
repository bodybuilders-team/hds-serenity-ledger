package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferMessage;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.shared.models.Account;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private final NodeService nodeService;
    private final ProcessLogger logger;
    private final ClientProcessConfig[] clientsConfig; // All clients configuration

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    private List<LedgerMessageDto> accumulatedMessages = new ArrayList<>();
    private final int accumulationThreshold = 3;

    public HDSLedgerService(
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService,
            ClientProcessConfig[] clientsConfig
    ) {
        this.nodeService = nodeService;
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.logger = new ProcessLogger(HDSLedgerService.class.getName(), nodeService.getConfig().getId());
        this.clientsConfig = clientsConfig;
    }

//    /**
//     * Handles a register message.
//     *
//     * @param message the register message
//     */
//    public void uponRegister(HDSLedgerMessage message) {
//        logger.info(MessageFormat.format("Received register request: {0}", message.getValue()));
//
//        try {
//            String ownerId = message.getValue();
//            String publicKey = CryptoUtils.getPublicKey(ownerId).toString();
//
//            nodeService.getLedger().addAccount(publicKey, new Account(ownerId, publicKey));
//
//            // Send the response
//            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.REGISTER_RESPONSE)
//                    .setValue("Account created successfully")
//                    .build();
//
//            authenticatedPerfectLink.send(message.getSenderId(), response);
//        } catch (Exception e) {
//            logger.error(MessageFormat.format("Error registering account: {0}", e.getMessage()));
//        }
//    }

    /**
     * Handles a transfer message.
     *
     * @param message the transfer message
     */
    public void uponTransfer(LedgerMessageDto message) {
        logger.info(MessageFormat.format("Received transfer request: {0}", message.getValue()));

        try {

            if (message.verifySignature(clientsConfig)) return;

            //TODO: remove double deserialization
            var transferMessage = SerializationUtils.deserialize(message.getValue(), LedgerTransferMessage.class);

            // Accumulate messages
            accumulateOrPropose(message);

            // Send the response
            LedgerMessageDto response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.TRANSFER_RESPONSE)
                    .setValue(MessageFormat.format("Transfer from {0} to {1} of {2} was successful",
                            transferMessage.getSourceAccountId(), transferMessage.getDestinationAccountId(), transferMessage.getAmount()))
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error transferring: {0}", e.getMessage()));
        }
    }


    /**
     * Handles a balance message.
     *
     * @param message the balance message
     */
    public void uponBalance(LedgerMessageDto message) {
        logger.info("Received balance request");

        try {
            String accountId = message.getValue();
            ClientProcessConfig owner = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny().get();
            PublicKey publicKey = CryptoUtils.getPublicKey(owner.getPublicKeyPath());

            Account account = nodeService.getLedger().getAccount(publicKey.toString());
            long balance = account.getBalance();

            logger.info(MessageFormat.format("Sending balance response: {0}", balance));

            LedgerMessageDto response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.BALANCE_RESPONSE)
                    .setValue(String.valueOf(balance))
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error retrieving balance: {0}", e.getMessage()));
        }
    }


    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof LedgerMessageDto ledgerMessageDto))
                        continue;

                    new Thread(() -> {
                        switch (ledgerMessageDto.getType()) {
//                            case REGISTER -> uponRegister(ledgerMessage);

                            case BALANCE -> uponBalance(ledgerMessageDto);

                            case TRANSFER -> uponTransfer(ledgerMessageDto);
                            case IGNORE -> {/* Do nothing */}

                            default ->
                                    logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerMessageDto.getType()));
                        }
                    }).start();

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }

    private void accumulateOrPropose(LedgerMessageDto ledgerMessageDto) {
        accumulatedMessages.add(ledgerMessageDto);
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
