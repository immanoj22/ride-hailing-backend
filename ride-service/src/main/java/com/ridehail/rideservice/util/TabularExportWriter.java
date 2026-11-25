package com.ridehail.rideservice.util;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Shared csv/pdf/excel writer for vehicleTypeExport / vehicleVariantExport (legacy used exceljs +
 * pdfkit and wrote directly to res). These endpoints bypass the JSON/ApiResponseService envelope -
 * they return a raw {@link ResponseEntity} with a Content-Disposition attachment header, matching
 * the legacy behaviour.
 */
public final class TabularExportWriter {

    private TabularExportWriter() {
    }

    public static ResponseEntity<?> write(String fileType, String baseName, String title,
                                          List<String> columns, List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No data to export"));
        }
        return switch (fileType == null ? "" : fileType) {
            case "csv" -> csv(baseName, columns, rows);
            case "pdf" -> pdf(baseName, title, columns, rows);
            case "excel" -> excel(baseName, title, columns, rows);
            default -> ResponseEntity.badRequest().body(Map.of("message", "Invalid fileType"));
        };
    }

    private static ResponseEntity<?> csv(String baseName, List<String> columns, List<Map<String, String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columns)).append("\n");
        for (Map<String, String> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(nz(row.get(columns.get(i)))).append("\"");
            }
            sb.append("\n");
        }
        return body(baseName + ".csv", MediaType.parseMediaType("text/csv"), sb.toString().getBytes());
    }

    private static ResponseEntity<?> excel(String baseName, String title,
                                           List<String> columns, List<Map<String, String>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(title);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columns.get(i));
            }
            int r = 1;
            for (Map<String, String> row : rows) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < columns.size(); i++) {
                    xr.createCell(i).setCellValue(nz(row.get(columns.get(i))));
                }
            }
            wb.write(out);
            return body(baseName + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Export failed"));
        }
    }

    private static ResponseEntity<?> pdf(String baseName, String title,
                                         List<String> columns, List<Map<String, String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Paragraph heading = new Paragraph(title);
            heading.setAlignment(Element.ALIGN_CENTER);
            doc.add(heading);
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100);
            for (String col : columns) {
                PdfPCell cell = new PdfPCell(new Paragraph(col));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            for (Map<String, String> row : rows) {
                for (String col : columns) {
                    PdfPCell cell = new PdfPCell(new Paragraph(nz(row.get(col))));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            doc.close();
            return body(baseName + ".pdf", MediaType.APPLICATION_PDF, out.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Export failed"));
        }
    }

    private static ResponseEntity<byte[]> body(String filename, MediaType type, byte[] data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(data, headers, 200);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
