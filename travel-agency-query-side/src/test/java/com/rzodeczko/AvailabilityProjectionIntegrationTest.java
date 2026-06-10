package com.rzodeczko;

import com.rzodeczko.avro.AvailabilityUpdatedAvro;
import com.rzodeczko.avro.BookingCreatedAvro;
import com.rzodeczko.avro.HotelUpsertedAvro;
import com.rzodeczko.domain.model.AvailabilityStatus;
import com.rzodeczko.infrastructure.persistence.document.AvailabilityDocument;
import com.rzodeczko.infrastructure.persistence.repository.MongoDailyAvailabilityRepository;
import com.rzodeczko.infrastructure.persistence.repository.MongoHotelRepository;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;


import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests: real Kafka (embedded) + real MongoDB (Testcontainers).
 *
 * <p>Three levels of coverage:
 * <ol>
 *   <li>Listener only — produce {@code AvailabilityUpdated} directly, verify MongoDB upsert.</li>
 *   <li>Hotel listener — produce {@code HotelUpserted}, verify hotel document and capacity cache.</li>
 *   <li>Full pipeline — produce {@code BookingCreated}, wait for Kafka Streams to emit
 *       {@code AvailabilityUpdated}, then verify MongoDB projection with the correct status.</li>
 * </ol>
 *
 * <p><strong>Schema Registry:</strong> {@code mock://integration-test} (in-process, no Docker needed).
 * All components — Kafka Streams serdes, consumer deserializers, and the test producer — share the
 * same in-memory registry via the same URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "travel.bookings",
                "travel.availability",
                "travel.availability.DLT",
                "travel.hotels",
                "travel.hotels.DLT"
        },
        brokerProperties = {
                "auto.create.topics.enable=true",           // Kafka Streams creates internal topics at runtime
                "transaction.state.log.min.isr=1",          // required for single-broker setup
                "transaction.state.log.replication.factor=1"
        }
)
@Testcontainers
@ActiveProfiles("integration-test")
class AvailabilityProjectionIntegrationTest {

    private static final String MOCK_REGISTRY = "mock://integration-test";

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.3.1");


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // override the ${SPRING_DATA_MONGODB_URI} placeholder in application.yaml
        registry.add("spring.mongodb.uri", mongoDBContainer::getConnectionString);
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getConnectionString);
    }

    @Value("${spring.embedded.kafka.brokers}")
    private String brokerAddresses;

    @Autowired
    private MongoDailyAvailabilityRepository availabilityRepository;

    @Autowired
    private MongoHotelRepository hotelRepository;


    private KafkaTemplate<String, Object> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerAddresses);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put("schema.registry.url", MOCK_REGISTRY);

        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));

        availabilityRepository.deleteAll();
        hotelRepository.deleteAll();
    }


    @Test
    void shouldUpsertAvailabilityDocumentWhenAvailabilityUpdatedEventArrives() {
        AvailabilityUpdatedAvro event = AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(1L)
                .setDate("2024-06-01")
                .setOccupied(3L)
                .build();

        producer.send("travel.availability", "1", event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(1L, LocalDate.of(2024, 6, 1)));

            assertThat(doc).isPresent();
            assertThat(doc.get().getHotelId()).isEqualTo(1L);
            assertThat(doc.get().getDate()).isEqualTo(LocalDate.of(2024, 6, 1));
            assertThat(doc.get().getOccupied()).isEqualTo(3L);
            assertThat(doc.get().getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void shouldOverwriteExistingDocumentOnSubsequentAvailabilityUpdatedEvent() {
        // first event
        producer.send("travel.availability", "1", AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(1L).setDate("2024-06-02").setOccupied(2L).build());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(availabilityRepository.findById(
                        AvailabilityDocument.buildId(1L, LocalDate.of(2024, 6, 2)))).isPresent()
        );

        // updated count arrives
        producer.send("travel.availability", "1", AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(1L).setDate("2024-06-02").setOccupied(5L).build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(1L, LocalDate.of(2024, 6, 2)));
            assertThat(doc).isPresent();
            assertThat(doc.get().getOccupied()).isEqualTo(5L);
        });
    }


    @Test
    void shouldPersistHotelDocumentWhenHotelUpsertedEventArrives() {
        producer.send("travel.hotels", "2", HotelUpsertedAvro.newBuilder()
                .setHotelId(2L)
                .setCapacity(120L)
                .build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<?> hotel = hotelRepository.findById(2L);
            assertThat(hotel).isPresent();
        });
    }

    @Test
    void shouldUseHotelCapacityFromMongoWhenCalculatingAvailabilityStatus() {
        // 1. Store hotel capacity via listener
        producer.send("travel.hotels", "3", HotelUpsertedAvro.newBuilder()
                .setHotelId(3L).setCapacity(10L).build());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(hotelRepository.findById(3L)).isPresent());

        // 2. Produce availability event with 9 out of 10 rooms occupied -> LAST_ROOMS (threshold 0.9)
        producer.send("travel.availability", "3", AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(3L).setDate("2024-06-10").setOccupied(9L).build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(3L, LocalDate.of(2024, 6, 10)));

            assertThat(doc).isPresent();
            assertThat(doc.get().getCapacity()).isEqualTo(10L);
            assertThat(doc.get().getOccupied()).isEqualTo(9L);
            assertThat(doc.get().getStatus()).isEqualTo(AvailabilityStatus.LAST_ROOMS);
        });
    }

    @Test
    void shouldSetSoldOutStatusWhenAllRoomsOccupied() {
        producer.send("travel.hotels", "4", HotelUpsertedAvro.newBuilder()
                .setHotelId(4L).setCapacity(10L).build());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(hotelRepository.findById(4L)).isPresent());

        producer.send("travel.availability", "4", AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(4L).setDate("2024-06-10").setOccupied(10L).build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(4L, LocalDate.of(2024, 6, 10)));

            assertThat(doc).isPresent();
            assertThat(doc.get().getStatus()).isEqualTo(AvailabilityStatus.SOLD_OUT);
        });
    }

    // Full pipeline: BookingCreated -> Kafka Streams → MongoDB

    /**
     * Exercises the complete event flow:
     * <ol>
     *   <li>HotelUpserted → hotel capacity stored in MongoDB</li>
     *   <li>BookingCreated → Kafka Streams flatMaps to daily keys → KTable aggregates → AvailabilityUpdated</li>
     *   <li>AvailabilityUpdated → AvailabilityProjectionListener → MongoDB upsert</li>
     * </ol>
     * A 30-second timeout accommodates Kafka Streams consumer-group rebalance and state-store
     * initialization on the first run.
     */
    @Test
    void shouldProjectBookingCreatedEventThroughKafkaStreamsToMongoDB() {
        final long hotelId = 99L;
        final String date = "2024-08-01";

        // Step 1: establish hotel capacity
        producer.send("travel.hotels", String.valueOf(hotelId), HotelUpsertedAvro.newBuilder()
                .setHotelId(hotelId).setCapacity(20L).build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(hotelRepository.findById(hotelId)).isPresent());

        // Step 2: booking arrives on travel.bookings
        producer.send("travel.bookings", "booking-99-1", BookingCreatedAvro.newBuilder()
                .setId(1L)
                .setHotelId(hotelId)
                .setUserId(100L)
                .setStart(date)
                .setEnd(date)   // single-night booking
                .build());

        // Step 3: wait for the full pipeline to complete
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(hotelId, LocalDate.of(2024, 8, 1)));

            assertThat(doc).isPresent();
            assertThat(doc.get().getHotelId()).isEqualTo(hotelId);
            assertThat(doc.get().getDate()).isEqualTo(LocalDate.of(2024, 8, 1));
            assertThat(doc.get().getOccupied()).isEqualTo(1L);
            assertThat(doc.get().getCapacity()).isEqualTo(20L);
            assertThat(doc.get().getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        });
    }

    @Test
    void shouldAggregateMultipleBookingsForSameDayThroughFullPipeline() {
        final long hotelId = 98L;
        final String date = "2024-09-15";

        producer.send("travel.hotels", String.valueOf(hotelId), HotelUpsertedAvro.newBuilder()
                .setHotelId(hotelId).setCapacity(5L).build());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(hotelRepository.findById(hotelId)).isPresent());

        producer.send("travel.bookings", "booking-98-1", BookingCreatedAvro.newBuilder()
                .setId(1L).setHotelId(hotelId).setUserId(101L).setStart(date).setEnd(date).build());
        producer.send("travel.bookings", "booking-98-2", BookingCreatedAvro.newBuilder()
                .setId(2L).setHotelId(hotelId).setUserId(102L).setStart(date).setEnd(date).build());
        producer.send("travel.bookings", "booking-98-3", BookingCreatedAvro.newBuilder()
                .setId(3L).setHotelId(hotelId).setUserId(103L).setStart(date).setEnd(date).build());
        producer.send("travel.bookings", "booking-98-4", BookingCreatedAvro.newBuilder()
                .setId(4L).setHotelId(hotelId).setUserId(104L).setStart(date).setEnd(date).build());
        producer.send("travel.bookings", "booking-98-5", BookingCreatedAvro.newBuilder()
                .setId(5L).setHotelId(hotelId).setUserId(105L).setStart(date).setEnd(date).build());

        // 5 rooms booked out of 5 -> SOLD_OUT
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<AvailabilityDocument> doc = availabilityRepository.findById(
                    AvailabilityDocument.buildId(hotelId, LocalDate.of(2024, 9, 15)));

            assertThat(doc).isPresent();
            assertThat(doc.get().getOccupied()).isEqualTo(5L);
            assertThat(doc.get().getStatus()).isEqualTo(AvailabilityStatus.SOLD_OUT);
        });
    }
}
