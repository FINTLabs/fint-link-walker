package no.fintlabs.linkwalker.report;

import no.fintlabs.linkwalker.task.Task;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ReportService {

    public byte[] createSpreadSheet(Task task) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Report");
            var headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Status Code");
            headerRow.createCell(1).setCellValue("Relation Link");
            headerRow.createCell(2).setCellValue("Self IDs");

            int rowNum = 1;
            for (var entryReport : task.getEntryReports()) {
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
