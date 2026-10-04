package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTarbilDiseasesUseCaseTest {

    @Mock private TarbilDiseaseRepository repository;

    @Test
    void should_buildCategoryPaths_when_listing() {
        UUID category = UUID.fromString("53fe7807-e179-4a57-b4c2-fb5d59f1882d");
        UUID leaf = UUID.fromString("95860589-0057-42cc-8209-663e1e459594");
        UUID rootLeaf = UUID.fromString("afe6153c-f045-4c51-a13d-e4b2e6ffbb0e");
        when(repository.findAllOrdered()).thenReturn(List.of(
            TarbilDisease.of(category, null, "SİNDİRİM SİSTEMİ HASTALIKLARI", false, 1),
            TarbilDisease.of(leaf, category, "PARAZİTER HASTALIKLAR", true, 2),
            TarbilDisease.of(rootLeaf, null, "METABOLİZMA HASTALIKLARI", true, 3)));

        List<TarbilDiseaseSummary> result = new ListTarbilDiseasesUseCase(repository).execute();

        assertThat(result).extracting(TarbilDiseaseSummary::path).containsExactly(
            "SİNDİRİM SİSTEMİ HASTALIKLARI", "SİNDİRİM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR", "METABOLİZMA HASTALIKLARI");
        assertThat(result).extracting(TarbilDiseaseSummary::selectable).containsExactly(false, true, true);
        assertThat(result.get(1).parentId()).isEqualTo(category);
    }
}
