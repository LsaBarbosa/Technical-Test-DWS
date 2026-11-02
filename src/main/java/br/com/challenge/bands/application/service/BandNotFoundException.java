package br.com.challenge.bands.application.service;

import java.util.UUID;

public class BandNotFoundException extends RuntimeException {

    public BandNotFoundException(UUID id) {
        super("Band not found: " + id);
    }
}
