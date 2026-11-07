package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.Dependent;
import java.util.List;

/** rolledBack=false ise hicbir sey silinmemistir; blockers engelleyen klinik kayitlaridir. */
public record RollbackResult(boolean rolledBack, List<Dependent> blockers) {
}
