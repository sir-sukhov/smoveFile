package sir.sukhov.smovefile;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import sir.sukhov.smovefile.targets.Destination;
import sir.sukhov.smovefile.targets.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws ConfigurationException, InterruptedException {

        Configurations configs = new Configurations();
        XMLConfiguration config;
        try {
            config = configs.xml("config.xml");
        }
        catch (ConfigurationException e) {
            logger.log(Level.INFO, "Can't find config in default location, trying src/main/resources/config.xml");
            config = configs.xml("src/main/resources/config.xml");
        }

        List<HierarchicalConfiguration<ImmutableNode>> flows = config.configurationsAt("flows.flow");
        final ExecutorService service = Executors.newFixedThreadPool(flows.size());

        List<Future> flowTasks =  new ArrayList<>();
        for (HierarchicalConfiguration<ImmutableNode> flow : flows) {
            Source source = new Source((String) flow.getProperty("source.host"),
                    Integer.parseInt((String) flow.getProperty("source.port")),
                    (String) flow.getProperty("source.user"),
                    ((String) flow.getProperty("source.identity")).replace("~", System.getProperty("user.home")),
                    ((String) flow.getProperty("source.knownHosts")).replace("~", System.getProperty("user.home")),
                    (String) flow.getProperty("source.path"),
                    flow.getList(String.class, "source.templates.template"));
            Destination destination = new Destination((String) flow.getProperty("destination.host"),
                    Integer.parseInt((String) flow.getProperty("destination.port")),
                    (String) flow.getProperty("destination.user"),
                    ((String) flow.getProperty("destination.identity")).replace("~", System.getProperty("user.home")),
                    ((String) flow.getProperty("destination.knownHosts")).replace("~", System.getProperty("user.home")),
                    (String) flow.getProperty("destination.path"));
            flowTasks.add(service.submit(new Flow(
                    source,
                    destination,
                    Integer.parseInt((String) flow.getProperty("executors")),
                    Integer.parseInt((String) flow.getProperty("bandwidth")))));
        }
        while (true) {
            if (anyTaskIsDone(flowTasks)) {
                service.shutdown();
                throw new RuntimeException("Child task exited");
            }
            else  {
                Thread.sleep(1000);
            }
        }

    }

    private static boolean anyTaskIsDone(List<Future> futures) {
        for (Future task : futures) {
            if (task.isDone()) {
                return true;
            }
        }
        return false;
    }

}
