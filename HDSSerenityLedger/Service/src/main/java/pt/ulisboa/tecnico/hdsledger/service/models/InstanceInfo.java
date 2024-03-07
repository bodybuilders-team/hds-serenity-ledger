package pt.ulisboa.tecnico.hdsledger.service.models;


import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;

/**
 * Information about a specific consensus instance.
 */
public class InstanceInfo {

    private int currentRound = 1;
    private int preparedRound = -1;
    private String preparedValue = null;
    private CommitMessage commitMessage;
    private String inputValue;
    private int committedRound = -1;

    public InstanceInfo(String inputValue) {
        this.inputValue = inputValue;
    }

    /**
     * Check if the instance has already decided.
     * @return true if the instance has already decided, false otherwise.
     */
    public boolean alreadyDecided() {
        return committedRound != -1;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public String getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(String preparedValue) {
        this.preparedValue = preparedValue;
    }

    public String getInputValue() {
        return inputValue;
    }

    public void setInputValue(String inputValue) {
        this.inputValue = inputValue;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setDecidedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }
}
