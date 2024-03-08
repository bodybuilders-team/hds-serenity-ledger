package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage { // TODO: Can be record

    private final String value;

    public CommitMessage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
