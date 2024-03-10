package pt.ulisboa.tecnico.hdsledger.service.models;


/**
 * Information about a specific consensus instance.
 */
public class InstanceInfo {

    private String inputValue;
    private int currentRound = 1;
    private int preparedRound = -1;
    private String preparedValue = null;
    private int decidedRound = -1;
    private String decidedValue = null;

    public InstanceInfo(String inputValue) {
        this.inputValue = inputValue;
    }

    /**
     * Check if the instance has already decided.
     *
     * @return true if the instance has already decided, false otherwise.
     */
    public boolean alreadyDecided() {
        return decidedRound != -1;
    }

    public String getInputValue() {
        return inputValue;
    }

    public void setInputValue(String inputValue) {
        this.inputValue = inputValue;
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

    public int getDecidedRound() {
        return decidedRound;
    }

    public void setDecidedRound(int committedRound) {
        this.decidedRound = committedRound;
    }

    public String getDecidedValue() {
        return decidedValue;
    }

    public void setDecidedValue(String decidedValue) {
        this.decidedValue = decidedValue;
    }
}
