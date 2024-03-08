package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Objects;

/**
 * Stores a prepared round and value pair.
 */
public class PreparedRoundValuePair { // TODO: can be a record
    private final int round;
    private final String value;

    public PreparedRoundValuePair(int round, String value) {
        this.round = round;
        this.value = value;
    }

    public int getRound() {
        return round;
    }

    public String getValue() {
        return value;
    }

    public boolean isNull() {
        return round == -1 && value == null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PreparedRoundValuePair preparedRoundValuePair)) {
            return false;
        }

        return preparedRoundValuePair.round == round &&
                ((preparedRoundValuePair.value == null && value == null) ||
                        (preparedRoundValuePair.value != null && preparedRoundValuePair.value.equals(value))
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(round, value);
    }
}
