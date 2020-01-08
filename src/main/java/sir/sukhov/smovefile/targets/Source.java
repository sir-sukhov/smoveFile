package sir.sukhov.smovefile.targets;

import java.util.List;

public class Source extends Target {

    private List<String> templates;

    public Source(String host, int port, String user, String identity, String knownHosts, String path, List<String> templates) {
        super(host, port, user, identity, knownHosts, path);
        this.templates = templates;
    }

    public List<String> getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "Source={" +
                super.toString() + ", " +
                "templates=" + templates +
                '}';
    }
}
