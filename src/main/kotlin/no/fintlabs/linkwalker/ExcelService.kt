package no.fintlabs.linkwalker

import no.fintlabs.linkwalker.model.RelationReport
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class ExcelService {

    fun createSpreadSheet(relationErrors: Collection<RelationReport>): ByteArray =
        XSSFWorkbook().use { wb ->
            ByteArrayOutputStream().use { out ->
                val sheet = wb.createSheet("Report")

                var nextRow = 0
                nextRow = createOverviewSection(sheet, relationErrors, nextRow)
                nextRow += 2

                if (relationErrors.any { it.unknownSize > 0 }) {
                    nextRow = createUnknownLinkSection(sheet, relationErrors, nextRow)
                    nextRow += 2
                }

                createRelationErrorSection(sheet, relationErrors, nextRow)

                (0..8).forEach(sheet::autoSizeColumn)

                wb.write(out)
                out.toByteArray()
            }
        }

    /* -------- sections ---------- */

    private fun createOverviewSection(
        sheet: XSSFSheet,
        reports: Collection<RelationReport>,
        startRow: Int
    ): Int {
        var row = startRow
        sheet.createRow(row++).apply {
            createCell(0).setCellValue("Relasjon endepunkt")
            createCell(1).setCellValue("Antall relasjon feil")
            createCell(2).setCellValue("Antall ukjente lenker")
        }
        reports.forEach {
            sheet.createRow(row++).apply {
                createCell(0).setCellValue(it.baseUrl)
                createCell(1).setCellValue(it.relationErrors.size.toString())
                createCell(2).setCellValue(it.unknownLinks.size.toString())
            }
        }
        return row
    }

    private fun createUnknownLinkSection(
        sheet: XSSFSheet,
        reports: Collection<RelationReport>,
        startRow: Int
    ): Int {
        var row = startRow
        sheet.createRow(row++).apply {
            createCell(0).setCellValue("Ukjent lenke")
            createCell(1).setCellValue("id til ressursen")
        }
        reports.flatMap { it.unknownLinks }.forEach {
            sheet.createRow(row++).apply {
                createCell(3).setCellValue(it.relation)
                createCell(4).setCellValue(it.selfLink)
            }
        }
        return row
    }

    private fun createRelationErrorSection(
        sheet: XSSFSheet,
        reports: Collection<RelationReport>,
        startRow: Int
    ) {
        var row = startRow
        sheet.createRow(row++).apply {
            createCell(0).setCellValue("Relasjon feil")
            createCell(1).setCellValue("id til ressursen")
        }
        reports.flatMap { it.relationErrors }.forEach {
            sheet.createRow(row++).apply {
                createCell(0).setCellValue(it.relation)
                createCell(1).setCellValue(it.selfLink)
            }
        }
    }
}
