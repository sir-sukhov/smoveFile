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
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import sir.sukhov.smovefile.targets.Destination;
import sir.sukhov.smovefile.targets.Source;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Main.class);
    private static ExecutorService flowService;
    private static List<ExecutorService> moveServices = new ArrayList<>();
    private static List<Future> flowTasks = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {

        XMLConfiguration config = null;

        String CONFIG_FILE_NAME = "config.xml";
        if (!(new File("config.xml")).exists()) {
            CONFIG_FILE_NAME = "src/main/resources/config.xml";
        }

        try {
            StreamSource schemaSource = new StreamSource(Main.class.getClassLoader().getResourceAsStream("config.xsd"));
            Schema schema = SchemaFactory
                    .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(schemaSource);
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setSchema(schema);
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            docBuilder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception)  throws SAXException {
                    throw exception;
                }
            });
            Parameters params = new Parameters();
            FileBasedConfigurationBuilder<XMLConfiguration> builder =
                    new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
                            .configure(params.xml()
                                    .setFileName(CONFIG_FILE_NAME)
                                    .setDocumentBuilder(docBuilder));
            config = builder.getConfiguration();
        } catch (ConfigurationException | ParserConfigurationException | SAXException e) {
            //handle exception
            log.error("Error on building configuration", e);
            System.exit(1);
        }

        List<HierarchicalConfiguration<ImmutableNode>> flows = config.configurationsAt("flows.flow");
        flowService = Executors.newFixedThreadPool(flows.size());

        Runtime.getRuntime().addShutdownHook(new Thread("ShutdownHook")
        {
            public void run()
            {
                log.info("Shutdown sequence start");
                flowService.shutdownNow();
                //Shutdown all move services
                for (int i = 0; i < moveServices.size(); i++) {
                    log.warn("Sending shutdown to move service " + i);
                    moveServices.get(i).shutdown();
                }
                //Wait for all move services termination
                for (ExecutorService service : moveServices) {
                    try {
                        if (!service.awaitTermination(60, TimeUnit.SECONDS)) {
                            service.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        log.error("Interrupted on awaiting moveService termination", e);
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
