package com.rzodeczko.infrastructure.streams;

import com.rzodeczko.avro.AvailabilityUpdatedAvro;
import com.rzodeczko.avro.BookingCreatedAvro;
import com.rzodeczko.infrastructure.configuration.properties.AppTopicsProperties;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Kafka Streams topology using {@link TopologyTestDriver}.
 * No Spring context, no Docker — fast and deterministic.
 */
class BookingStreamsTopologyTest {

    /**
     * mock:// is the in-process schema registry provided by confluent's kafka-schema-registry-client.
     * All serdes configured with the same URL share the same in-memory registry instance.
     */
    private static final String SCHEMA_REGISTRY_URL = "mock://streams-topology-test";

    private static final String BOOKINGS_TOPIC    = "travel.bookings";
    private static final String AVAILABILITY_TOPIC = "travel.availability";

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, BookingCreatedAvro>     bookingsTopic;
    private TestOutputTopic<String, AvailabilityUpdatedAvro> availabilityTopic;

    @BeforeEach
    void setUp() {
        AppTopicsProperties topics = new AppTopicsProperties(
                BOOKINGS_TOPIC, AVAILABILITY_TOPIC, "travel.availability.DLT",
                "travel.hotels", "travel.hotels.DLT"
        );

        BookingStreamsTopology topologyConfig = new BookingStreamsTopology(topics);
        ReflectionTestUtils.setField(topologyConfig, "schemaRegistryUrl", SCHEMA_REGISTRY_URL);

        StreamsBuilder builder = new StreamsBuilder();
        topologyConfig.availabilityTopology(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-streams-topology");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put("schema.registry.url", SCHEMA_REGISTRY_URL);

        testDriver = new TopologyTestDriver(builder.build(), props);

        Map<String, Object> serdeConfig = Map.of("schema.registry.url", SCHEMA_REGISTRY_URL);

        SpecificAvroSerde<BookingCreatedAvro> bookingSerde = new SpecificAvroSerde<>();
        bookingSerde.configure(serdeConfig, false);

        SpecificAvroSerde<AvailabilityUpdatedAvro> availabilitySerde = new SpecificAvroSerde<>();
        availabilitySerde.configure(serdeConfig, false);

        bookingsTopic = testDriver.createInputTopic(
                BOOKINGS_TOPIC, new StringSerializer(), bookingSerde.serializer());
        availabilityTopic = testDriver.createOutputTopic(
                AVAILABILITY_TOPIC, new StringDeserializer(), availabilitySerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    // ── single booking ────────────────────────────────────────────────────────

    @Test
    void shouldProduceOneEventForSingleDayBooking() {
        bookingsTopic.pipeInput("k1", booking(1L, 10L, "2024-06-01", "2024-06-01"));

        List<KeyValue<String, AvailabilityUpdatedAvro>> events = availabilityTopic.readKeyValuesToList();

        assertThat(events).hasSize(1);
        AvailabilityUpdatedAvro e = events.get(0).value;
        assertThat(e.getHotelId()).isEqualTo(10L);
        assertThat(e.getDate()).isEqualTo("2024-06-01");
        assertThat(e.getOccupied()).isEqualTo(1L);
    }

    @Test
    void shouldExpandMultiNightBookingToOneEventPerNight() {
        bookingsTopic.pipeInput("k1", booking(1L, 10L, "2024-06-01", "2024-06-03"));

        List<KeyValue<String, AvailabilityUpdatedAvro>> events = availabilityTopic.readKeyValuesToList();

        // 3 nights → 3 events (one per date)
        assertThat(events).hasSize(3);
        assertThat(events)
                .extracting(kv -> kv.value.getDate())
                .containsExactlyInAnyOrder("2024-06-01", "2024-06-02", "2024-06-03");
    }

    // ── aggregation ───────────────────────────────────────────────────────────

    @Test
    void shouldAggregateOccupancyAcrossBookingsForSameDay() {
        bookingsTopic.pipeInput("k1", booking(1L, 10L, "2024-06-01", "2024-06-01"));
        bookingsTopic.pipeInput("k2", booking(2L, 10L, "2024-06-01", "2024-06-01"));
        bookingsTopic.pipeInput("k3", booking(3L, 10L, "2024-06-01", "2024-06-01"));

        List<KeyValue<String, AvailabilityUpdatedAvro>> all = availabilityTopic.readKeyValuesToList();

        // KTable emits on every state change; the last event carries the final count
        AvailabilityUpdatedAvro latest = all.get(all.size() - 1).value;
        assertThat(latest.getHotelId()).isEqualTo(10L);
        assertThat(latest.getDate()).isEqualTo("2024-06-01");
        assertThat(latest.getOccupied()).isEqualTo(3L);
    }

    @Test
    void shouldKeepOccupancyIsolatedBetweenDifferentHotels() {
        bookingsTopic.pipeInput("k1", booking(1L, 10L, "2024-06-01", "2024-06-01"));
        bookingsTopic.pipeInput("k2", booking(2L, 20L, "2024-06-01", "2024-06-01"));

        List<KeyValue<String, AvailabilityUpdatedAvro>> events = availabilityTopic.readKeyValuesToList();

        assertThat(events).hasSize(2);
        assertThat(events)
                .extracting(kv -> kv.value.getHotelId())
                .containsExactlyInAnyOrder(10L, 20L);
        // each hotel has exactly 1 booking
        events.forEach(kv -> assertThat(kv.value.getOccupied()).isEqualTo(1L));
    }

    @Test
    void shouldKeepOccupancyIsolatedBetweenDifferentDatesForSameHotel() {
        bookingsTopic.pipeInput("k1", booking(1L, 10L, "2024-06-01", "2024-06-01"));
        bookingsTopic.pipeInput("k2", booking(2L, 10L, "2024-06-02", "2024-06-02"));

        List<KeyValue<String, AvailabilityUpdatedAvro>> events = availabilityTopic.readKeyValuesToList();

        assertThat(events).hasSize(2);
        events.forEach(kv -> assertThat(kv.value.getOccupied()).isEqualTo(1L));
        assertThat(events)
                .extracting(kv -> kv.value.getDate())
                .containsExactlyInAnyOrder("2024-06-01", "2024-06-02");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BookingCreatedAvro booking(long id, long hotelId, String start, String end) {
        return BookingCreatedAvro.newBuilder()
                .setId(id)
                .setHotelId(hotelId)
                .setUserId(999L)
                .setStart(start)
                .setEnd(end)
                .build();
    }
}
