package no.novari.linkwalker.report

import no.novari.linkwalker.OrgId

interface ReportRetention {
    fun purgeOldScans(orgId: OrgId): PurgedScans
}

data class PurgedScans(val summariesDeleted: Int, val problemsDeleted: Long) {
    companion object {
        val NOTHING = PurgedScans(0, 0)
    }
}
