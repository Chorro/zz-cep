package io.wizzie.cep.integration;

import io.wizzie.bootstrapper.builder.Config;
import io.wizzie.cep.builder.Builder;
import io.wizzie.cep.controllers.SiddhiController;
import io.wizzie.cep.serializers.JsonDeserializer;
import io.wizzie.cep.serializers.JsonSerializer;
import kafka.utils.MockTime;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.integration.utils.EmbeddedKafkaCluster;
import org.apache.kafka.streams.integration.utils.IntegrationTestUtils;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class DifferentOutputTest {

    private static int NUM_BROKERS = 1;

    @ClassRule
    public static EmbeddedKafkaCluster CLUSTER2 = new EmbeddedKafkaCluster(NUM_BROKERS);
    private final static MockTime MOCK_TIME = CLUSTER2.time;

    @Test
    public void differentOutputsTest() throws Exception {
        Config config = new Config();
        String appId = UUID.randomUUID().toString();
        config.put("application.id", "test2");
        config.put("bootstrap.servers", CLUSTER2.bootstrapServers());
        config.put("num.stream.threads", 1);
        config.put("bootstrapper.classname", "io.wizzie.bootstrapper.bootstrappers.impl.KafkaBootstrapper");
        List<String> bootstrapTopics = new LinkedList<>();
        bootstrapTopics.add("__cep_bootstrap");
        config.put("bootstrap.kafka.topics", bootstrapTopics);
        config.put("multi.id", false);
        config.put("metric.enable", true);
        List<String> listeners = new LinkedList<>();
        listeners.add("io.wizzie.metrics.listeners.KafkaMetricListener");
        config.put("metric.listeners", listeners);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Builder builder = new Builder(config.clone(), SiddhiController.TEST_CreateInstance());

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER2.bootstrapServers());
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("two_outputs_query.json").getFile());

        String jsonConfig = getFileContent(file);

        KeyValue<String, String> jsonConfigKv = new KeyValue<>("test2", jsonConfig);
        IntegrationTestUtils.produceKeyValuesSynchronously("__cep_bootstrap", Collections.singletonList(jsonConfigKv), producerConfig, MOCK_TIME);

        Map<String, Object> message1 = new HashMap<>();

        message1.put("timestamp", 1122334455L);

        KeyValue<String, Map<String, Object>> kvStream1 = new KeyValue<>("KEY_A", message1);

        Properties producerConfigA = new Properties();
        producerConfigA.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER2.bootstrapServers());
        producerConfigA.put(ProducerConfig.ACKS_CONFIG, "all");
        producerConfigA.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerConfigA.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Serdes.String().serializer().getClass());
        producerConfigA.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        IntegrationTestUtils.produceKeyValuesSynchronously("kafkainput7", Collections.singletonList(kvStream1), producerConfigA, MOCK_TIME);

        Properties consumerConfig = new Properties();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER2.bootstrapServers());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-consumer-B");
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<KeyValue<String, Map<String, Object>>> receivedMessagesFromOutput = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfig, "kafkaoutput6", 1);
        List<KeyValue<String, Map<String, Object>>> receivedMessagesFromOutput2 = IntegrationTestUtils.waitUntilMinKeyValueRecordsReceived(consumerConfig, "kafkaoutput7", 1);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("timestamp", 1122334455L);

        KeyValue<String, Map<String, Object>> expectedDataKv = new KeyValue<>("KEY_A", expectedData);

        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput);
        assertEquals(Collections.singletonList(expectedDataKv), receivedMessagesFromOutput2);
    }

    private static String getFileContent(File file) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

        StringBuilder stringBuffer = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {

            stringBuffer.append(line).append("\n");
        }
        return stringBuffer.toString();
    }
}
