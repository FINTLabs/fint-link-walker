package no.fint.linkwalker;

import no.fint.linkwalker.data.PwfUtils;
import no.fint.linkwalker.dto.Status;
import no.fint.linkwalker.dto.TestCase;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Collection;
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

        private final short FONT_SIZE_DEFAULT = 12;
        private final short FONT_SIZE_HEADER = 16;


        private Workbook workbook;
        private Sheet detailsSheet;
        private Sheet summarySheet;
        private TestCase testCase;


        private WorkbookBuilder(TestCase testCase) {
            workbook = new XSSFWorkbook();
            detailsSheet = workbook.createSheet("Details");
            summarySheet = workbook.createSheet("Summary");
            this.testCase = testCase;

            setupDetailsSheet();
        }

        public Workbook build() {

            setupDetailsSheet();
            setupSummarySheet();
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

        private void setupDetailsSheet() {
            detailsSheet.setAutoFilter(CellRangeAddress.valueOf("A1:E1"));
            detailsSheet.createFreezePane(0, 1);


            detailsSheet.setColumnWidth(COL_A, 25 * 256);
            detailsSheet.setColumnWidth(COL_B, 15 * 256);
            detailsSheet.setColumnWidth(COL_C, 25 * 256);
            detailsSheet.setColumnWidth(COL_D, 100 * 256);
            detailsSheet.setColumnWidth(COL_E, 100 * 256);
        }

        private void setupSummarySheet() {
            summarySheet.setColumnWidth(COL_A, 25 * 256);
        }

        private void setupHeader() {

            Row header = detailsSheet.createRow(0);

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
            headerCell.setCellValue("Relation URL");
            headerCell.setCellStyle(headerStyle);

            headerCell = header.createCell(COL_E);
            headerCell.setCellValue("Parent URL");
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
            Row totalSummaryRow = summarySheet.createRow(1);
            Row failedSummaryRow = summarySheet.createRow(2);
            Row okSummaryRow = summarySheet.createRow(3);

            CellStyle defaultCellStyle = getDefaultCellStyle();
            Font font = getDefaultFont();
            font.setBold(true);
            defaultCellStyle.setFont(font);

            Cell cell = totalSummaryRow.createCell(COL_A);
            Cell cellFormula = totalSummaryRow.createCell(COL_B);
            cell.setCellValue("Total test count");
            //cellFormula.setCellFormula("COUNTA(Details!A:A) - 1");
            cellFormula.setCellValue(testCase.getRelations().values().stream().map(Collection::size).reduce(Integer::sum).get());
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

            cell = failedSummaryRow.createCell(COL_A);
            cellFormula = failedSummaryRow.createCell(COL_B);
            cell.setCellValue("Total failed count");
            cellFormula.setCellFormula("COUNTIF(Details!B:B, \"FAILED\")");
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

            cell = okSummaryRow.createCell(COL_A);
            cellFormula = okSummaryRow.createCell(COL_B);
            cell.setCellValue("Total success count");
            cellFormula.setCellFormula("B2-B3");
            cell.setCellStyle(defaultCellStyle);
            cellFormula.setCellStyle(defaultCellStyle);

        }

        private void setupReportData() {
            CellStyle style = getDefaultCellStyle();

            CellStyle hRefCellStyle = getHRefCellStyle();


            CreationHelper createHelper = workbook.getCreationHelper();

            AtomicInteger rowCount = new AtomicInteger(1);
            TestCase filteredTestCase = testCase.filterAndCopyRelations(Status.FAILED);
            filteredTestCase.getRelations().forEach((s, testedRelations) -> testedRelations.forEach(testedRelation -> {
                Row row = detailsSheet.createRow(rowCount.get());
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

                cell = row.createCell(COL_E);
                link.setAddress(convertToTestClientUrl(testedRelation.getParentUrl().toString()));
                cell.setHyperlink(link);
                cell.setCellValue(testedRelation.getParentUrl().toString());
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
