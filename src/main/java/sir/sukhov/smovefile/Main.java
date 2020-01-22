/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static ExecutorService flowService;
    private static List<ExecutorService> moveServices = new ArrayList<>();
    private static List<Future> flowTasks = new ArrayList<>();

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
        flowService = Executors.newFixedThreadPool(flows.size());

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                logger.log(Level.INFO, "Starting shutdown sequence");
                flowService.shutdownNow();
                //Shutdown all move services
                for (int i = 0; i < moveServices.size(); i++) {
                    System.out.println("Sending shutdown to move service " + i);
                    moveServices.get(i).shutdown();
                }
                //Wait for all move services termination
                for (ExecutorService service : moveServices) {
                    try {
                        if (!service.awaitTermination(60, TimeUnit.SECONDS)) {
                            service.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        for (int i = 0; i < flows.size(); i++) {
            HierarchicalConfiguration<ImmutableNode> flow = flows.get(i);
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
            moveServices.add(i,Executors.newFixedThreadPool(Integer.parseInt((String) flow.getProperty("executors"))));
            flowTasks.add(i, flowService.submit(new Flow(
                    source,
                    destination,
                    Integer.parseInt((String) flow.getProperty("executors")),
                    Integer.parseInt((String) flow.getProperty("bandwidth")),
                    moveServices.get(i))));
        }
        while (true) {
            if (anyTaskIsDone(flowTasks)) {
                System.exit(1);
            }
            else  {
                Thread.sleep(500);
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
