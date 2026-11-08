package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import com.vetos.modules.consentform.domain.exception.FormSourceImageNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.storage.StoredFileNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GetFormSourceImageUseCaseTest {
    private final FormSourceImageRepository repository = mock(FormSourceImageRepository.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private final GetFormSourceImageUseCase subject = new GetFormSourceImageUseCase(repository, storage);
    private final UUID imageId = UUID.randomUUID();

    private void existingImage() {
        FormSourceImage image = mock(FormSourceImage.class);
        when(image.getContentType()).thenReturn("image/webp");
        when(image.getStorageRef()).thenReturn("opaque-storage-ref");
        when(repository.findById(imageId)).thenReturn(Optional.of(image));
    }

    @Test
    void returnsStoredBytesAndContentType() {
        existingImage();
        byte[] bytes = {82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80};
        when(storage.retrieve("opaque-storage-ref")).thenReturn(bytes);
        var result = subject.execute(imageId);
        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.content()).containsExactly(bytes);

        verify(storage).retrieve("opaque-storage-ref");
        verify(repository, never()).save(any());
        verify(storage, never()).store(any(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    void missingRecordIsNotFoundWithoutStorageAccess() {
        when(repository.findById(imageId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> subject.execute(imageId))
            .isInstanceOf(FormSourceImageNotFoundException.class);
        verifyNoInteractions(storage);
        verify(repository, never()).save(any());
    }

    @Test
    void missingStoredFileDoesNotReturnEmptyOrSuccessfulContent() {
        existingImage();
        var failure = new StoredFileNotFoundException("opaque-storage-ref");
        when(storage.retrieve("opaque-storage-ref")).thenThrow(failure);
        assertThatThrownBy(() -> subject.execute(imageId)).isSameAs(failure);
        verify(repository, never()).save(any());
        verify(storage, never()).delete(any());
    }

    @Test
    void storageFailureIsNotHiddenAsMissingImage() {
        existingImage();
        var failure = new IllegalStateException("storage unavailable");
        when(storage.retrieve("opaque-storage-ref")).thenThrow(failure);
        assertThatThrownBy(() -> subject.execute(imageId)).isSameAs(failure);
        verify(repository, never()).save(any());
        verify(storage, never()).delete(any());
    }
}
