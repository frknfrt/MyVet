package com.vetos.modules.inventory.domain;

import java.util.List;

/** settledCount: dusulen bekleyen satir sayisi; unmatched: hicbir bekleyenle eslesmeyen bildirilen satirlar. */
public record DeductionSettlementResult(int settledCount, List<ReportedDeductionLine> unmatched) {}
