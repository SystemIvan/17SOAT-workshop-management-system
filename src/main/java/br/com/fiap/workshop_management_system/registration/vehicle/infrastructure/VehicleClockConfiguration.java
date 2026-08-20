package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class VehicleClockConfiguration {

    @Bean
    Clock vehicleClock() {
        return Clock.systemDefaultZone();
    }
}
