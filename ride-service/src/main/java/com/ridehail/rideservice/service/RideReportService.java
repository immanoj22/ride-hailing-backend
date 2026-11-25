package com.ridehail.rideservice.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.rideservice.client.RideAxios;
import com.ridehail.rideservice.entity.Ride;
import com.ridehail.rideservice.entity.RideAdminPayment;
import com.ridehail.rideservice.entity.RideDriverPayment;
import com.ridehail.rideservice.entity.RideProcess;
import com.ridehail.rideservice.entity.VehicleType;
import com.ridehail.rideservice.entity.VehicleVariant;
import com.ridehail.rideservice.repository.RideRepository;
import com.ridehail.rideservice.repository.VehicleTypeRepository;
import com.ridehail.rideservice.repository.VehicleVariantRepository;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Ports ride.controller.js: export_report, generate_ride_invoice. Both bypass the JSON envelope. */
@Service
@Transactional(readOnly = true)
public class RideReportService {

    private static final DateTimeFormatter IST =
            DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm:ss a").withZone(ZoneId.of("Asia/Kolkata"));

    private static final String[] COLUMNS = {
            "Ride ID", "Vehicle", "Driver Name", "Driver Details", "User Name", "User Details",
            "Ride Type", "Pick Up", "Drop Off", "Status", "Distance", "Fare", "User Rating", "Driver Rating"
    };

    private final RideRepository rideRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final VehicleVariantRepository vehicleVariantRepository;
    private final RideAxios axios;

    public RideReportService(RideRepository rideRepository, VehicleTypeRepository vehicleTypeRepository,
            VehicleVariantRepository vehicleVariantRepository, RideAxios axios) {
        this.rideRepository = rideRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.vehicleVariantRepository = vehicleVariantRepository;
        this.axios = axios;
    }

    public record Export(byte[] body, String contentType, String filename) {
    }

    public Export exportReport(String type, String search, String fromDate, String toDate, String rideType) {
        final String searchValue = search == null ? null : search.trim();
        final OffsetDateTime from = parseDay(fromDate, false);
        final OffsetDateTime to = parseDay(toDate, true);

        Specification<Ride> spec = (root, q, cb) -> {
            var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.isFalse(root.get("deleted")));
            if (rideType != null && !rideType.isBlank()) {
                preds.add(cb.equal(root.get("rideType"), rideType));
            }
            if (searchValue != null && !searchValue.isEmpty()) {
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("bookingNumber")), "%" + searchValue.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("paymentMethod")), "%" + searchValue.toLowerCase() + "%")));
            }
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Ride> rides = rideRepository.findAll(spec,
                org.springframework.data.domain.PageRequest.of(0, 100000,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent();

        Map<UUID, VehicleType> typeMap = vehicleTypeRepository.findAllById(
                        rides.stream().map(Ride::getVehicleTypeId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(VehicleType::getId, v -> v));
        Map<UUID, VehicleVariant> variantMap = vehicleVariantRepository.findAllById(
                        rides.stream().map(Ride::getVehicleVariantId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(VehicleVariant::getId, v -> v));

        Map<String, JsonNode> userMap = new LinkedHashMap<>();
        Map<String, JsonNode> driverMap = new LinkedHashMap<>();
        List<String> userIds = rides.stream().map(Ride::getUserId).filter(Objects::nonNull).map(UUID::toString).distinct().toList();
        List<String> driverIds = rides.stream().map(Ride::getDriverId).filter(Objects::nonNull).map(UUID::toString).distinct().toList();
        if (!userIds.isEmpty()) {
            collectProfiles(axios.getUsersByIds(userIds), userMap);
        }
        if (!driverIds.isEmpty()) {
            collectProfiles(axios.getDriversByIds(Map.of("driverIds", driverIds)), driverMap);
        }

        List<Map<String, String>> data = new ArrayList<>();
        for (Ride ride : rides) {
            JsonNode driver = ride.getDriverId() == null ? null : driverMap.get(ride.getDriverId().toString());
            JsonNode user = ride.getUserId() == null ? null : userMap.get(ride.getUserId().toString());

            String driverName = driver == null ? "-"
                    : orDash(join(" ", txt(driver, "firstName"), txt(driver, "lastName")));
            String driverDetails = driver == null ? "-"
                    : orDash(join("\n", txt(driver, "mobile"), txt(driver, "email")));
            String userName = user == null ? "-"
                    : orDash(join(" ", txt(user, "firstname"), txt(user, "lastname")));
            String userDetails = user == null ? "-"
                    : orDash(join("\n", txt(user, "mobile"), txt(user, "email")));

            VehicleType vt = typeMap.get(ride.getVehicleTypeId());
            VehicleVariant vv = variantMap.get(ride.getVehicleVariantId());
            String vehicle = (vt == null ? "" : nz(vt.getLabel())) + " - " + (vv == null ? "" : nz(vv.getLabel()));

            String pickUp = orDash(join(" ", ride.getLocationPickup(), ride.getLocationPickupDistrict()));
            String dropOff = orDash(join(" ", ride.getLocationDropoff(), ride.getLocationDropoffDistrict()));

            BigDecimal grandTotal = jsonMoney(ride.getTotalPrice(), "grandTotal");

            Map<String, String> row = new LinkedHashMap<>();
            row.put("Ride ID", orDash(ride.getBookingNumber()));
            row.put("Vehicle", orDash(vehicle));
            row.put("Driver Name", driverName);
            row.put("Driver Details", driverDetails);
            row.put("User Name", userName);
            row.put("User Details", userDetails);
            row.put("Ride Type", orDash(ride.getRideType()));
            row.put("Pick Up", pickUp);
            row.put("Drop Off", dropOff);
            row.put("Status", orDash(ride.getStatus()));
            row.put("Distance", ride.getKm() == null ? "0" : ride.getKm().toPlainString());
            row.put("Fare", grandTotal.signum() != 0
                    ? "₹ " + grandTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "₹ 0");
            row.put("User Rating", "-");
            row.put("Driver Rating", "-");
            data.add(row);
        }

        if (data.isEmpty()) {
            throw new ApiException(StatusCode.BAD_REQUEST, "No data to export");
        }

        String fileType = type == null ? "" : type;
        return switch (fileType) {
            case "csv" -> new Export(buildCsv(data), "text/csv", "rides.csv");
            case "pdf" -> new Export(buildPdf(data), "application/pdf", "rides.pdf");
            case "excel" -> new Export(buildExcel(data),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "rides.xlsx");
            default -> throw new ApiException(StatusCode.BAD_REQUEST, "Invalid fileType");
        };
    }

    private byte[] buildCsv(List<Map<String, String>> data) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", COLUMNS));
        for (Map<String, String> row : data) {
            List<String> cells = new ArrayList<>();
            for (String c : COLUMNS) {
                cells.add("\"" + nz(row.get(c)).replace("\n", " ") + "\"");
            }
            lines.add(String.join(",", cells));
        }
        return String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] buildExcel(List<Map<String, String>> data) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Rides");
            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                header.createCell(i).setCellValue(COLUMNS[i]);
                sheet.setColumnWidth(i, 25 * 256);
            }
            int r = 1;
            for (Map<String, String> row : data) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < COLUMNS.length; i++) {
                    xr.createCell(i).setCellValue(nz(row.get(COLUMNS[i])).replace("\n", " "));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to build excel export");
        }
    }

    private byte[] buildPdf(List<Map<String, String>> data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Paragraph title = new Paragraph("Ride Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(COLUMNS.length);
            table.setWidthPercentage(100);
            Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font cf = FontFactory.getFont(FontFactory.HELVETICA, 7);
            for (String c : COLUMNS) {
                PdfPCell cell = new PdfPCell(new Paragraph(c, hf));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            for (Map<String, String> row : data) {
                for (String c : COLUMNS) {
                    PdfPCell cell = new PdfPCell(new Paragraph(nz(row.get(c)).replace("\n", " "), cf));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to build pdf export");
        }
    }

    // ---- generate_ride_invoice ------------------------------------------------

    public Export generateInvoice(String rideId) {
        UUID rideUuid;
        try {
            rideUuid = UUID.fromString(rideId);
        } catch (Exception e) {
            throw new ApiException(StatusCode.BAD_REQUEST, "Invalid rideId");
        }
        Ride ride = rideRepository.findById(rideUuid)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Ride not found"));

        JsonNode user = profileData(axios.getUserById(ride.getUserId()));
        JsonNode driver = ride.getDriverId() == null ? null : profileData(axios.getDriverById(ride.getDriverId()));

        VehicleType vt = ride.getVehicleTypeId() == null ? null
                : vehicleTypeRepository.findById(ride.getVehicleTypeId()).orElse(null);
        VehicleVariant vv = ride.getVehicleVariantId() == null ? null
                : vehicleVariantRepository.findById(ride.getVehicleVariantId()).orElse(null);

        String driverName = driver == null ? "-"
                : orDash(join(" ", firstNonBlank(txt(driver, "firstName"), txt(driver, "firstname")),
                        firstNonBlank(txt(driver, "lastName"), txt(driver, "lastname"))));
        String userName = user == null ? "-"
                : orDash(firstNonBlank(join(" ", txt(user, "firstname"), txt(user, "lastname")),
                        txt(user, "fullname"), txt(user, "email")));

        JsonNode tp = ride.getTotalPrice();
        Map<String, String> tripTypeMap = Map.of("local", "Local", "outstation", "Outstation",
                "oneway", "One Way", "round", "Round Trip", "rental", "Rental");
        String rideTypeText = tripTypeMap.getOrDefault(ride.getRideType(), orDash(ride.getRideType()));

        RideProcess start = ride.getProcess().stream().filter(p -> "startride".equals(p.getStatus())).findFirst().orElse(null);
        RideProcess drop = ride.getProcess().stream().filter(p -> "dropoff".equals(p.getStatus())).findFirst().orElse(null);

        BigDecimal adminTotal = ride.getAdminPayment().stream()
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal driverTotal = ride.getDriverPayment().stream()
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph header = new Paragraph("RIDEHAIL", h1);
            doc.add(header);
            doc.add(new Paragraph("www.ridehailcabservice.com  |  9876543210", normal));
            doc.add(new Paragraph("Building No. 14/334, Palm Grove Enclave, Kottayam - 686002", normal));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("INVOICE", label));
            doc.add(new Paragraph("Booking ID : " + firstNonBlank(ride.getBookingNumber(), rideId), normal));
            doc.add(new Paragraph("Trip Type  : " + rideTypeText, normal));
            doc.add(new Paragraph("Vehicle    : " + (vt == null ? "-" : nz(vt.getLabel())) + " - "
                    + (vv == null ? "-" : nz(vv.getLabel())), normal));
            doc.add(new Paragraph("Reg No.    : " + (driver == null ? "-" : orDash(txt(driver, "vehicleNumber"))), normal));
            doc.add(new Paragraph("Start  : " + fmt(start == null ? null : start.getCreatedAt()), normal));
            doc.add(new Paragraph("Finish : " + fmt(drop == null ? null : drop.getCreatedAt()), normal));
            doc.add(new Paragraph("Pickup : " + orDash(firstNonBlank(ride.getLocationPickup(), ride.getLocationPickupDistrict())), normal));
            doc.add(new Paragraph("Dropoff: " + orDash(firstNonBlank(ride.getLocationDropoff(), ride.getLocationDropoffDistrict())), normal));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("DRIVER", label));
            doc.add(new Paragraph(driverName, normal));
            doc.add(new Paragraph(driver == null ? "-" : orDash(txt(driver, "email")), normal));
            doc.add(new Paragraph(driver == null ? "-" : orDash(txt(driver, "mobile")), normal));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("PASSENGER", label));
            doc.add(new Paragraph(userName, normal));
            doc.add(new Paragraph(user == null ? "-" : orDash(txt(user, "email")), normal));
            doc.add(new Paragraph(user == null ? "-" : orDash(txt(user, "mobile")), normal));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            addFareRow(table, "Base Price", money(tp, "basePrice"));
            addFareRow(table, "Base Fare", money(tp, "baseFare"));
            addFareRow(table, "Driver Allowance", money(tp, "driverAllowance"));
            addFareRow(table, "Insurance", money(tp, "insurance"));
            addFareRow(table, "Peak Time Charge", money(tp, "peakTimeCharge"));
            addFareRow(table, "Price Surge", money(tp, "priceSurge"));
            addFareRow(table, "Toll Tax", money(tp, "tollPrice"));
            addFareRow(table, "Kilometer Price", money(tp, "kilometerPrice"));
            addFareRow(table, "Minutes Price", money(tp, "minutePrice"));
            addFareRow(table, "Waiting Charges", money(tp, "waitingAmount"));
            addFareRow(table, "Total", money(tp, "total"));
            addFareRow(table, "Commission", money(tp, "commission"));
            BigDecimal promo = jsonMoney(tp, "promo");
            if (promo.signum() > 0) {
                addFareRow(table, "Discount", "-Rs." + promo.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            }
            addFareRow(table, "Sub Total", money(tp, "subtotal"));
            addFareRow(table, "GST", money(tp, "gstPrice"));
            addFareRow(table, "GRAND TOTAL", "Rs." + jsonMoney(tp, "grandTotal")
                    .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            doc.add(table);

            if (!ride.getAdminPayment().isEmpty() || !ride.getDriverPayment().isEmpty()) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Payment Settlement", label));
                PdfPTable settle = new PdfPTable(3);
                settle.setWidthPercentage(100);
                for (RideAdminPayment p : ride.getAdminPayment()) {
                    settleRow(settle, "Admin", paymentMethod(p.getTransaction(), ride.getPaymentMethod()),
                            "Rs." + money2(p.getAmount()));
                }
                for (RideDriverPayment p : ride.getDriverPayment()) {
                    settleRow(settle, "Driver", paymentMethod(p.getTransaction(), ride.getPaymentMethod()),
                            "Rs." + money2(p.getAmount()));
                }
                if (adminTotal.signum() > 0) {
                    settleRow(settle, "Total Admin", "", "Rs." + money2(adminTotal));
                }
                if (driverTotal.signum() > 0) {
                    settleRow(settle, "Total Driver", "", "Rs." + money2(driverTotal));
                }
                doc.add(settle);
            }

            doc.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Thank you for riding with RIDEHAIL. Drive safe!", normal);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);
            doc.close();

            return new Export(out.toByteArray(), "application/pdf",
                    "invoice_" + firstNonBlank(ride.getBookingNumber(), rideId) + ".pdf");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to generate invoice");
        }
    }

    private void addFareRow(PdfPTable t, String label, String amount) {
        if (amount == null) {
            return;
        }
        t.addCell(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        PdfPCell c = new PdfPCell(new Paragraph(amount, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private void settleRow(PdfPTable t, String party, String method, String amount) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8);
        t.addCell(new Paragraph(party, f));
        t.addCell(new Paragraph(method, f));
        PdfPCell c = new PdfPCell(new Paragraph(amount, f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c);
    }

    private String money(JsonNode tp, String field) {
        BigDecimal v = jsonMoney(tp, field);
        return v.signum() > 0 ? "Rs." + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : null;
    }

    private String money2(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String paymentMethod(JsonNode transaction, String fallback) {
        if (transaction != null) {
            String t = transaction.path("paymentInstrument").path("type").asText(null);
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return orDash(fallback);
    }

    private static BigDecimal jsonMoney(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.path(field).asText("0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void collectProfiles(JsonNode resp, Map<String, JsonNode> target) {
        if (resp == null || !resp.path("status").asBoolean(false)) {
            return;
        }
        JsonNode arr = resp.path("data");
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                String id = node.path("_id").asText(null);
                if (id != null) {
                    target.put(id, node);
                }
            }
        }
    }

    private JsonNode profileData(JsonNode resp) {
        if (resp == null) {
            return null;
        }
        JsonNode data = resp.path("data");
        if (data.has("user")) {
            return data.path("user");
        }
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private OffsetDateTime parseDay(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
            return endOfDay
                    ? d.atTime(23, 59, 59, 999_000_000).atOffset(java.time.ZoneOffset.UTC)
                    : d.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(OffsetDateTime dt) {
        return dt == null ? "-" : IST.format(dt);
    }

    private static String txt(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static String join(String sep, String... parts) {
        List<String> nonBlank = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                nonBlank.add(p);
            }
        }
        return String.join(sep, nonBlank);
    }

    private static String firstNonBlank(String... parts) {
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return "";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
