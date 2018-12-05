package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.concurrent.atomic.AtomicInteger;

public class ReportWorkbook {




    public static ReportWorkbook builder() {
        return new ReportWorkbook();
    }

    public WorkbookBuilder of(TestCase testCase) {
        return new WorkbookBuilder(testCase);
    }

    public class WorkbookBuilder {

        private final int COL_A = 0;
        private final int COL_B = 1;
        private final int COL_C = 2;
        private final int COL_D = 3;
        private final int COL_E = 4;
        private final int COL_F = 5;
        private final int COL_G = 6;

        private final short FONT_SIZE_DEFAULT = 12;
        private final short FONT_SIZE_HEADER = 16;


        private Workbook workbook;
        private Sheet sheet;
        private TestCase testCase;


        private WorkbookBuilder(TestCase testCase) {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet();
            this.testCase = testCase;

            setupSheet();
        }

        public Workbook build() {

            setupSheet();
            setupHeader();
            setupReportData();
            setupSummary();

            return workbook;
        }

        private Font getDefaultFont() {
            Font font = workbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints(FONT_SIZE_DEFAULT);
            return font;
        }

        private void setupSheet() {
            sheet.setAutoFilter(CellRangeAddress.valueOf("A1:D1"));
            sheet.createFreezePane(0, 1);


            sheet.setColumnWidth(COL_A, 25 * 256);
            sheet.setColumnWidth(COL_B, 15 * 256);
            sheet.setColumnWidth(COL_C, 25 * 256);
            sheet.setColumnWidth(COL_D, 100 * 256);
            sheet.setColumnWidth(COL_F, 20 * 256);
        }

        private void setupHeader() {

            Row header = sheet.createRow(0);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = getDefaultFont();
            font.setFontHeightInPoints(FONT_SIZE_HEADER);
            font.setBold(true);
            headerStyle.setFont(font);

            Cell headerCell = header.createCell(COL_A);
            headerCell.setCellValue("Relation");
            headerCell.setCellStyle(headerStyle);

            headerCell = header.createCell(COL_B);
            headerCell.setCellValue("Status");
            headerCell.setCellStyle(headerStyle);

            headerCell = header.createCell(COL_C);
            headerCell.setCellValue("Reason");
            headerCell.setCellStyle(headerStyle);

            headerCell = header.createCell(COL_D);
            headerCell.setCellValue("URL");
            headerCell.setCellStyle(headerStyle);
        }

        private CellStyle getHRefCellStyle() {
            CellStyle hlinkStyle = getDefaultCellStyle();
            Font hrefFont = getDefaultFont();
            hrefFont.setUnderline(Font.U_SINGLE);
            hrefFont.setColor(IndexedColors.BLUE.getIndex());
            hlinkStyle.setFont(hrefFont);

            return hlinkStyle;
        }

        private CellStyle getDefaultCellStyle() {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            Font font = getDefaultFont();
            style.setFont(font);

            return style;
        }

        private void setupSummary() {
            Row totalSummaryRow = sheet.getRow(5);
            Row failedSummaryRow = sheet.getRow(6);
            Row okSummaryRow = sheet.getRow(7);

            CellStyle defaultCellStyle = getDefaultCellStyle();

            Cell cell = totalSummaryRow.createCell(COL_F);
            Cell cellFormula = totalSummaryRow.createCell(COL_G);
            cell.setCellValue("Total test count");
            cellFormula.setCellFormula("COUNTA(A:A) - 1");
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

            cell = failedSummaryRow.createCell(COL_F);
            cellFormula = failedSummaryRow.createCell(COL_G);
            cell.setCellValue("Total failed count");
            cellFormula.setCellFormula("COUNTIF(B:B, \"FAILED\")");
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

            cell = okSummaryRow.createCell(COL_F);
            cellFormula = okSummaryRow.createCell(COL_G);
            cell.setCellValue("Total success count");
            cellFormula.setCellFormula("COUNTIF(B:B, \"OK\")");
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

        }

        private void setupReportData() {
            CellStyle style = getDefaultCellStyle();

            CellStyle hRefCellStyle = getHRefCellStyle();


            CreationHelper createHelper = workbook.getCreationHelper();

            AtomicInteger rowCount = new AtomicInteger(1);
            testCase.getRelations().forEach((s, testedRelations) -> testedRelations.forEach(testedRelation -> {
                Row row = sheet.createRow(rowCount.get());
                Cell cell = row.createCell(COL_A);
                cell.setCellValue(s);
                cell.setCellStyle(style);

                cell = row.createCell(COL_B);
                cell.setCellValue(testedRelation.getStatus().name());
                cell.setCellStyle(style);

                cell = row.createCell(COL_C);
                cell.setCellValue(testedRelation.getReason());
                cell.setCellStyle(style);


                cell = row.createCell(COL_D);
                Hyperlink link = createHelper.createHyperlink(HyperlinkType.URL);
                link.setAddress(convertToTestClientUrl(testedRelation.getUrl().toString()));
                cell.setHyperlink(link);
                cell.setCellValue(testedRelation.getUrl().toString());
                cell.setCellStyle(hRefCellStyle);

                rowCount.getAndIncrement();
            }));
        }

        private String convertToTestClientUrl(String url) {
            if (PwfUtils.isPwf(url)) {
                return url;
            }
            return url.replace("felleskomponent.no/", "felleskomponent.no/?/");
        }
    }


}
