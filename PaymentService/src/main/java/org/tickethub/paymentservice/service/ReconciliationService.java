package org.tickethub.paymentservice.service;

import org.tickethub.paymentservice.dto.ReconciliationReport;

public interface ReconciliationService {
    ReconciliationReport runReconciliation();
    ReconciliationReport getLastReport();
}
