package no.novari.linkwalker.report.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// kotlin("plugin.jpa") generates the no-arg constructor Hibernate needs.
// The `internal` constructor below is the only way to make instances by hand;
// JpaReportStore.problemEntity() is the lone caller.
@Entity
@Table(
    name = "report_row",
    indexes = [
        Index(name = "idx_row_org_completed", columnList = "org_id, scan_completed_at DESC"),
        Index(name = "idx_row_filter", columnList = "org_id, scan_id, component, resource, problem_type"),
    ],
)
@SequenceGenerator(name = "report_row_seq", sequenceName = "report_row_seq", allocationSize = 1000)
class ReportProblemEntity internal constructor(
    @Column(name = "scan_id", nullable = false)
    val scanId: UUID,

    @Column(name = "org_id", nullable = false, length = 64)
    val orgId: String,

    @Column(name = "scan_completed_at", nullable = false)
    val scanCompletedAt: Instant,

    @Column(name = "component", nullable = false, length = 64)
    val component: String,

    @Column(name = "resource", nullable = false, length = 64)
    val resource: String,

    @Column(name = "problem_type", nullable = false, length = 64)
    val problemType: String,

    @Column(name = "source_self", nullable = false, columnDefinition = "TEXT")
    val sourceSelf: String,

    @Column(name = "target_href", nullable = false, columnDefinition = "TEXT")
    val targetHref: String,

    @Column(name = "relation_name", length = 128)
    val relationName: String? = null,

    @Column(name = "expected_inverse_name", length = 128)
    val expectedInverseName: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "report_row_seq")
    @Column(name = "id")
    val id: Long? = null,
)
