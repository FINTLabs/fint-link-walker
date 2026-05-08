package no.novari.linkwalker.report.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "report_row",
    indexes = [
        Index(name = "idx_row_org_completed", columnList = "org_id, scan_completed_at DESC"),
        Index(name = "idx_row_filter", columnList = "org_id, scan_id, component, resource, problem_type"),
    ],
)
class ReportRowEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "scan_id", nullable = false)
    var scanId: UUID = UUID.randomUUID(),

    @Column(name = "org_id", nullable = false, length = 64)
    var orgId: String = "",

    @Column(name = "scan_completed_at", nullable = false)
    var scanCompletedAt: Instant = Instant.EPOCH,

    @Column(name = "component", nullable = false, length = 64)
    var component: String = "",

    @Column(name = "resource", nullable = false, length = 64)
    var resource: String = "",

    @Column(name = "problem_type", nullable = false, length = 64)
    var problemType: String = "",

    @Column(name = "source_self", nullable = false, columnDefinition = "TEXT")
    var sourceSelf: String = "",

    @Column(name = "target_href", nullable = false, columnDefinition = "TEXT")
    var targetHref: String = "",

    @Column(name = "relation_name", length = 128)
    var relationName: String? = null,

    @Column(name = "expected_inverse_name", length = 128)
    var expectedInverseName: String? = null,
)
