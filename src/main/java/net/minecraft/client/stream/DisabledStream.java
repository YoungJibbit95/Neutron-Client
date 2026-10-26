package net.minecraft.client.stream;

/**
 * Compatibility endpoint for vanilla streaming calls. Pulsar does not start
 * the legacy Twitch SDK or load its native libraries.
 */
public final class DisabledStream extends NullStream {
    public DisabledStream() {
        super(new UnsupportedOperationException("Twitch broadcasting is disabled in this client."));
    }
}
