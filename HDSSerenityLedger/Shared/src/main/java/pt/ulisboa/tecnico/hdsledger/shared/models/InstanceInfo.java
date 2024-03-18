package pt.ulisboa.tecnico.hdsledger.shared.models;


/**
 * Information about a specific consensus instance.
 */
public class InstanceInfo {

    private Block inputValue;
    private int currentRound = 1;
    private int preparedRound = -1;
    private Block preparedValue = null;
    private int decidedRound = -1;
    private Block decidedValue = null;

    public InstanceInfo(Block inputValue) {
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

    public Block getInputValue() {
        return inputValue;
    }

    public void setInputValue(Block inputValue) {
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

    public Block getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(Block preparedValue) {
        this.preparedValue = preparedValue;
    }

    public int getDecidedRound() {
        return decidedRound;
    }

    public void setDecidedRound(int committedRound) {
        this.decidedRound = committedRound;
    }

    public Block getDecidedValue() {
        return decidedValue;
    }

    public void setDecidedValue(Block decidedValue) {
        this.decidedValue = decidedValue;
    }
}
