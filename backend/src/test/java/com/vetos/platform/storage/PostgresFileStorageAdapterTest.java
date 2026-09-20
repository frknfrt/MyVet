package com.vetos.platform.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresFileStorageAdapterTest {

    @Mock private StoredFileJpaRepository repository;

    @Test
    void should_saveContentAndReturnIdAsString_when_store() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        byte[] content = "hello".getBytes();

        String storageRef = adapter.store(content, "text/plain");

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository).save(captor.capture());
        StoredFile saved = captor.getValue();
        assertThat(saved.getId().toString()).isEqualTo(storageRef);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getContentType()).isEqualTo("text/plain");
    }

    @Test
    void should_returnContent_when_retrieve_and_storageRefExists() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        UUID id = UUID.randomUUID();
        byte[] content = "world".getBytes();
        StoredFile stored = StoredFile.create(id, content, "text/plain");
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        byte[] result = adapter.retrieve(id.toString());

        assertThat(result).isEqualTo(content);
    }

    @Test
    void should_throwStoredFileNotFound_when_retrieve_and_storageRefUnknown() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.retrieve(id.toString()))
            .isInstanceOf(StoredFileNotFoundException.class);
    }
}
