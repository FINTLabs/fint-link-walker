package no.fintlabs.linkwalker

import no.fintlabs.linkwalker.model.RelationReport
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class ExcelService {

    fun createSpreadSheet(relationErrors: Collection<RelationReport>): ByteArray =
        XSSFWorkbook().use { workbook ->
            ByteArrayOutputStream().use { out ->
                val sheet = workbook.createSheet("Report")

                createRelationErrorCells(sheet, relationErrors)
                createUnknownLinkCells(sheet, relationErrors)
                createOverviewCells(sheet, relationErrors)

                workbook.write(out)
                out.toByteArray()
            }
        }

    private fun createUnknownLinkCells(sheet: XSSFSheet, relationErrors: Collection<RelationReport>) {
        sheet.createRow(0).apply {
            createCell(3).setCellValue("Ukjent lenke")
            createCell(4).setCellValue("id til ressursen")
        }

        var rowNum = 1
        relationErrors.flatMap { it.unknownLinks }.forEach { unknownEntry ->
            sheet.createRow(rowNum++).apply {
                createCell(3).setCellValue(unknownEntry.relation)
                createCell(4).setCellValue(unknownEntry.selfLink)
            }
        }
    }

    private fun createOverviewCells(sheet: XSSFSheet, relationErrors: Collection<RelationReport>) {
        sheet.createRow(0).apply {
            createCell(6).setCellValue("Relasjon endepunkt")
            createCell(7).setCellValue("Antall relasjon feil")
            createCell(8).setCellValue("Antall ukjente lenker")
        }

        var rowNum = 1
        relationErrors.forEach { report ->
            sheet.createRow(rowNum++).apply {
                createCell(6).setCellValue(report.baseUrl)
                createCell(7).setCellValue(report.relationErrors.size.toString())
                createCell(8).setCellValue(report.unknownLinks.size.toString())
            }
        }
    }

    private fun createRelationErrorCells(sheet: XSSFSheet, relationErrors: Collection<RelationReport>) {
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Relasjon feil")
            createCell(1).setCellValue("id til ressursen")
        }

        var rowNum = 1
        relationErrors.flatMap { it.relationErrors }.forEach { errorEntry ->
            sheet.createRow(rowNum++).apply {
                createCell(0).setCellValue(errorEntry.relation)
                createCell(1).setCellValue(errorEntry.selfLink)
            }
        }
    }

}