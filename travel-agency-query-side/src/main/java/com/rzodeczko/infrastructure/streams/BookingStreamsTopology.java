package com.rzodeczko.infrastructure.streams;


import com.rzodeczko.avro.AvailabilityUpdatedAvro;
import com.rzodeczko.avro.BookingCreatedAvro;
import com.rzodeczko.infrastructure.configuration.properties.AppTopicsProperties;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BookingStreamsTopology {
    private final AppTopicsProperties topics;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Bean
    public KStream<String, AvailabilityUpdatedAvro> availabilityTopology(StreamsBuilder builder) {
        SpecificAvroSerde<BookingCreatedAvro> bookingSerde = new SpecificAvroSerde<>();
        bookingSerde.configure(Map.of("schema.registry.url", schemaRegistryUrl), false);

        SpecificAvroSerde<AvailabilityUpdatedAvro> availabilitySerde = new SpecificAvroSerde<>();
        availabilitySerde.configure(Map.of("schema.registry.url", schemaRegistryUrl), false);

        KStream<String, BookingCreatedAvro> bookings = builder.stream(
                topics.bookings(),
                Consumed.with(Serdes.String(), bookingSerde)
        );

        KStream<String, Long> perDay = bookings.flatMap((key, booking) -> {
            List<KeyValue<String, Long>> result = new ArrayList<>();
            LocalDate start = LocalDate.parse(booking.getStart());
            LocalDate end = LocalDate.parse(booking.getEnd());

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                String compositeKey = booking.getHotelId() + ":" + d;
                result.add(KeyValue.pair(compositeKey, 1L));
            }
            return result;
        });

        KTable<String, Long> occupancy = perDay
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .aggregate(
                        () -> 0L,
                        (k, value, agg) -> agg + value,
                        Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("occupancy-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Long())
                );

        KStream<String, AvailabilityUpdatedAvro> output = occupancy
                .toStream()
                .map((compositeKey, occupied) -> {
                    String[] parts = compositeKey.split(":", 2);
                    long hotelId = Long.parseLong(parts[0]);
                    String date = parts[1];

                    AvailabilityUpdatedAvro value = AvailabilityUpdatedAvro.newBuilder()
                            .setHotelId(hotelId)
                            .setDate(date)
                            .setOccupied(occupied)
                            .build();

                    return KeyValue.pair(String.valueOf(hotelId), value);
                });

        output.to(topics.availability(), Produced.with(Serdes.String(), availabilitySerde));
        return output;
    }
}
