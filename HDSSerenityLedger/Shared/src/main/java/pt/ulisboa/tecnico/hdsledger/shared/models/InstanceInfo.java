package pt.ulisboa.tecnico.hdsledger.shared.models;


import lombok.Getter;
import lombok.Setter;

/**
 * Information about a specific consensus instance.
 */
@Setter
@Getter
public class InstanceInfo {

    private Block inputValue = null;
    private int currentRound = 1;
    private int preparedRound = -1;
    private Block preparedValue = null;
    private int decidedRound = -1;
    private Block decidedValue = null;

    public InstanceInfo() {
    }

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
}
