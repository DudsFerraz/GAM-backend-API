package br.org.gam.api.shared.persistence;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

public class UUIDGenerator {
    private static final TimeBasedEpochGenerator uuidV7Generator = Generators.timeBasedEpochGenerator();

    public static UUID generateUUIDV7(){
        return uuidV7Generator.generate();
    }

    public static UUID generateUUIDV4(){
        return UUID.randomUUID();
    }
}
