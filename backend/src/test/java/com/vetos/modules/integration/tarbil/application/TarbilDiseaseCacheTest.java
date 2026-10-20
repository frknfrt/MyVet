package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Canli docker-compose Postgres gerekir (V72 tohumlu tarbil_disease). */
@SpringBootTest
class TarbilDiseaseCacheTest {

    @MockitoSpyBean private TarbilDiseaseRepository repository;
    @Autowired private ListTarbilDiseasesUseCase useCase;

    @Test
    void should_readTableOnce_and_returnSameImmutableList() {
        List<TarbilDiseaseSummary> first = useCase.execute();
        List<TarbilDiseaseSummary> second = useCase.execute();

        assertThat(first).isNotEmpty();
        assertThat(second).isSameAs(first);
        verify(repository, times(1)).findAllOrdered();
        assertThatThrownBy(() -> first.add(first.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }
}
