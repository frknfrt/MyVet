package com.vetos.platform.event;

/**
 * Modul-ici ve moduller-arasi domain event yayinlamak icin tek port.
 * Use-case siniflari infrastructure/Spring event API'sini degil, bu arayuzu bilir.
 */
public interface DomainEventPublisher {
    void publish(Object event);
}
