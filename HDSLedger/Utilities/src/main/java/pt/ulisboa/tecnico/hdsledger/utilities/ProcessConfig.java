package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {
    private boolean isLeader;
    private String hostname;
    private String id;
    private int port;

    public ProcessConfig() {
    }

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }


}
