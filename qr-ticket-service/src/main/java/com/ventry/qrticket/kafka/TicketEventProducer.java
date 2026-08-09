package com.ventry.qrticket.kafka;

import com.ventry.common.events.TicketGeneratedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventProducer {

    private static final String TICKET_GENERATED_TOPIC = "ticket.generated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTicketGenerated(TicketGeneratedEvent event) {
        kafkaTemplate.send(TICKET_GENERATED_TOPIC, event.bookingId(), event);
    }
}
