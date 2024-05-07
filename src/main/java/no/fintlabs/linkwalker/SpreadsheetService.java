package no.fintlabs.linkwalker;

import no.fintlabs.linkwalker.task.model.EntryReport;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class SpreadsheetService {

    public byte[] createSpreadSheet(List<EntryReport> entryReports) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Report");
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Status Code");
            headerRow.createCell(1).setCellValue("Relation Link");
            headerRow.createCell(2).setCellValue("Self IDs");

            int rowNum = 1;
            for (var entryReport : entryReports) {
                for (var relationError : entryReport.getRelationErrors()) {
                    var row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(relationError.statusCode());
                    row.createCell(1).setCellValue(relationError.relationUrl());
                    row.createCell(2).setCellValue(String.join(", ", entryReport.getSelfLinks()));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
