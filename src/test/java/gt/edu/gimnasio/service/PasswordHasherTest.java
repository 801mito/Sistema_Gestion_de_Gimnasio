package gt.edu.gimnasio.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void saltedHashesAreDistinctAndCanBeVerified() {
        char[] password = "clave-de-prueba-larga".toCharArray();
        String first = hasher.hash(password);
        String second = hasher.hash(password);

        assertNotEquals(first, second);
        assertTrue(first.startsWith("pbkdf2-sha256$600000$"));
        assertFalse(first.contains("clave-de-prueba-larga"));
        assertTrue(hasher.matches(password, first));
        assertTrue(hasher.matches(password, second));
        assertFalse(hasher.matches("otra-clave-larga".toCharArray(), first));
    }

    @Test
    void malformedHashesAndEmptyPasswordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("  ".toCharArray()));
        assertFalse(hasher.matches(null, "invalid"));
        assertFalse(hasher.matches("clave".toCharArray(), "invalid"));
        assertFalse(hasher.matches("clave".toCharArray(), "pbkdf2-sha256$999999999$AA==$AA=="));
        assertFalse(hasher.matches("clave".toCharArray(), "pbkdf2-sha256$600000$@@@$@@@"));
    }
}
