package no.novari.linkwalker.report.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "report_summary",
    indexes = [Index(name = "idx_summary_org_completed", columnList = "org_id, scan_completed_at DESC")],
)
class ReportSummaryEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "scan_id", nullable = false)
    var scanId: UUID = UUID.randomUUID(),

    @Column(name = "org_id", nullable = false, length = 64)
    var orgId: String = "",

    @Column(name = "scan_completed_at", nullable = false)
    var scanCompletedAt: Instant = Instant.EPOCH,

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    var summaryJson: String = "",
)
