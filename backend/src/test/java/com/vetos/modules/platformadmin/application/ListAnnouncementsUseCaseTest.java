package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Announcement;
import com.vetos.modules.platformadmin.domain.AnnouncementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAnnouncementsUseCaseTest {

    @Mock private AnnouncementRepository announcementRepository;

    @Test
    void should_returnAnnouncements_inRepositoryOrder() {
        Announcement a1 = Announcement.record("Baslik 1", "Govde 1", UUID.randomUUID(), "a@vetly.com.tr", 3);
        Announcement a2 = Announcement.record("Baslik 2", "Govde 2", UUID.randomUUID(), "b@vetly.com.tr", 5);
        when(announcementRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(a2, a1));
        ListAnnouncementsUseCase useCase = new ListAnnouncementsUseCase(announcementRepository);

        List<Announcement> result = useCase.execute();

        assertThat(result).containsExactly(a2, a1);
    }
}
