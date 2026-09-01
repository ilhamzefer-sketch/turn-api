package az.turn.api;

public interface PrivateAttachmentStorage {
    void store(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void deleteIfExists(String storageKey);
}
