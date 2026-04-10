package com.meridian.retail.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent at-rest encryption for String entity fields. Apply with:
 *
 *   @Convert(converter = EncryptedStringConverter.class)
 *   @Column(columnDefinition = "TEXT")
 *   private String sensitiveField;
 *
 * The column must be TEXT (or at least VARCHAR(512+)) to hold the Base64 ciphertext,
 * which is ~33% larger than the plaintext plus overhead for the IV/GCM tag.
 *
 * NOTE: Hibernate instantiates converters before Spring is fully ready in some startup
 * paths, so we resolve the EncryptionService lazily via {@link EncryptionService#instance()}
 * rather than injecting it. A @PostConstruct in EncryptionService publishes the singleton.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return EncryptionService.instance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return EncryptionService.instance().decrypt(dbData);
    }
}
