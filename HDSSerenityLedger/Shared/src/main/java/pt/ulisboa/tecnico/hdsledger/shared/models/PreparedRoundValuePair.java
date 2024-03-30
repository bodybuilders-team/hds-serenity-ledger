package pt.ulisboa.tecnico.hdsledger.shared.models;

/**
 * Stores a prepared round and value pair.
 */
public record PreparedRoundValuePair(int round, Block value) {

    public boolean isNull() {
        return round == -1 && value == null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof PreparedRoundValuePair preparedRoundValuePair))
            return false;

        return preparedRoundValuePair.round == round &&
                ((preparedRoundValuePair.value == null && value == null) ||
                        (preparedRoundValuePair.value != null && preparedRoundValuePair.value.equals(value)));
    }
}
