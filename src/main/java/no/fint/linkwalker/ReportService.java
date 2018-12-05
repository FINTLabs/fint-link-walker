package no.fint.linkwalker;

import no.fint.linkwalker.dto.TestCase;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ReportService {


    public ByteArrayInputStream export(TestCase testCase) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = ReportWorkbook.builder().of(testCase).build();
        workbook.write(out);

        return new ByteArrayInputStream(out.toByteArray());
    }
}