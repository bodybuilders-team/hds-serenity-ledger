package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Objects;

public class PreparedRoundValuePair {
    public int round;
    public String value;

    public PreparedRoundValuePair(int round, String value) {
        this.round = round;
        this.value = value;
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
