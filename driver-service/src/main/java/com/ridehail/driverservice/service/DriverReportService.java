package com.ridehail.driverservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.InternalServiceClient;
import com.ridehail.driverservice.dto.GetDriversRideReportRequest;
import com.ridehail.driverservice.dto.DriverExportReportResult;
import com.ridehail.driverservice.entity.Driver;
import com.ridehail.driverservice.entity.DriverWalletTransaction;
import com.ridehail.driverservice.repository.DriverRepository;
import com.ridehail.driverservice.repository.DriverWalletTransactionRepository;

/**
 * Direct port of driver.controller.js's get_my_rides / get_my_eranings / get_my_referral_data /
 * get_referral_earnings / get_driver_referrals_list / get_ref_driver_payment_details /
 * get_driver_dashboard_stats / get_drivers_report / get_drivers_ride_report / export_report /
 * update_referral (+ their driver.services.js/driver.axios.js helpers).
 */
@Service
public class DriverReportService {

    private static final PDFont PDF_TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont PDF_HEADER_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont PDF_BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final Set<String> COMPLETED_REFERRAL_STATUSES =
            Set.of("approved", "paid", "completed", "success");

    private final DriverRepository driverRepository;
    private final DriverWalletTransactionRepository driverWalletTransactionRepository;
    private final DriverReferralRewardService driverReferralRewardService;
    private final InternalServiceClient internalServiceClient;
    private final String rideServiceUrl;

    public DriverReportService(
            DriverRepository driverRepository,
            DriverWalletTransactionRepository driverWalletTransactionRepository,
            DriverReferralRewardService driverReferralRewardService,
            InternalServiceClient internalServiceClient,
            @Value("${ridehail.services.ride-url}") String rideServiceUrl) {
        this.driverRepository = driverRepository;
        this.driverWalletTransactionRepository = driverWalletTransactionRepository;
        this.driverReferralRewardService = driverReferralRewardService;
        this.internalServiceClient = internalServiceClient;
        this.rideServiceUrl = rideServiceUrl;
    }

    // ------------------------------------------------------------------
    // get_my_rides
    // ------------------------------------------------------------------

    public JsonNode getMyRides(UUID driverId, String status) {
        String effectiveStatus = (status == null || status.isBlank()) ? "active" : status;
        JsonNode rideRes = internalServiceClient.post(
                rideServiceUrl + "/driver/" + driverId + "/rides",
                Map.of("status", effectiveStatus));
        if (!rideRes.path("status").asBoolean(false)) {
            throw new ApiException(rideRes.path("statusCode").asInt(500),
                    textOrDefault(rideRes, "message", "Failed to fetch rides"));
        }
        return rideRes.path("data");
    }

    // ------------------------------------------------------------------
    // get_my_eranings
    // ------------------------------------------------------------------

    public JsonNode getMyEarnings(UUID driverId, int page, int limit, boolean pending) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", driverId.toString());
        payload.put("page", page > 0 ? page : 1);
        payload.put("limit", limit > 0 ? limit : 10);
        if (pending) {
            payload.put("isClaimed", false);
        }
        JsonNode earningRes = internalServiceClient.post(rideServiceUrl + "/driver-ride-payments", payload);
        if (!earningRes.path("status").asBoolean(false)) {
            throw new ApiException(earningRes.path("statusCode").asInt(500),
                    textOrDefault(earningRes, "message", "Failed to fetch earnings"));
        }
        return earningRes.path("data");
    }

    // ------------------------------------------------------------------
    // get_my_referral_data
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getMyReferralData(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        String referralCode = driver.getReferralCode() == null ? "" : driver.getReferralCode();
        long referralCount = driverRepository.countByReferredBy_IdAndDeletedFalse(driverId);

        List<DriverWalletTransaction> referralRewards =
                driverWalletTransactionRepository.findByDriver_IdAndTransactionType(driverId, "referral_bonus");

        BigDecimal totalReferralEarnings = BigDecimal.ZERO;
        BigDecimal pendingReferralEarnings = BigDecimal.ZERO;
        Set<String> pendingReferralDriverIds = new HashSet<>();

        for (DriverWalletTransaction reward : referralRewards) {
            String status = reward.getStatus() == null ? "" : reward.getStatus().toLowerCase(Locale.ROOT);
            if (COMPLETED_REFERRAL_STATUSES.contains(status)) {
                BigDecimal paid = reward.getPaidAmount();
                BigDecimal amount = (paid != null && paid.compareTo(BigDecimal.ZERO) != 0)
                        ? paid
                        : nullToZero(reward.getRequestedAmount());
                totalReferralEarnings = totalReferralEarnings.add(amount);
            } else if ("pending".equals(status)) {
                pendingReferralEarnings = pendingReferralEarnings.add(nullToZero(reward.getRequestedAmount()));
                JsonNode meta = reward.getMetaData();
                if (meta != null && meta.hasNonNull("referredDriverId")) {
                    pendingReferralDriverIds.add(meta.get("referredDriverId").asText());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("referralCode", referralCode);
        result.put("totalReferrals", referralCount);
        result.put("totalReferralEarnings", totalReferralEarnings);
        result.put("pendingReferralEarnings", pendingReferralEarnings);
        result.put("pendingReferrals", pendingReferralDriverIds.size());
        return result;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ------------------------------------------------------------------
    // get_referral_earnings
    // ------------------------------------------------------------------

    public JsonNode getReferralEarnings(UUID driverId) {
        JsonNode result = internalServiceClient.post(rideServiceUrl + "/driver-referral-earnings",
                Map.of("driverId", driverId.toString()));
        if (!result.path("success").asBoolean(false) && !result.path("status").asBoolean(false)) {
            throw new ApiException(500, textOrDefault(result, "message", "Failed to fetch referral earnings"));
        }
        return result.path("data");
    }

    // ------------------------------------------------------------------
    // get_driver_referrals_list
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDriverReferralsList(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        List<Driver> referrals = driverRepository.findByReferredBy_IdAndDeletedFalse(driver.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Driver r : referrals) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_id", r.getId());
            map.put("firstName", r.getFirstName());
            map.put("lastName", r.getLastName());
            map.put("mobile", r.getMobile());
            map.put("driverStatus", r.getDriverStatus());
            map.put("createdAt", r.getCreatedAt());
            map.put("lastTripAt", r.getLastTripAt());
            Map<String, Object> referredBy = new LinkedHashMap<>();
            referredBy.put("_id", driver.getId());
            referredBy.put("referralCode", driver.getReferralCode());
            map.put("referredBy", referredBy);
            result.add(map);
        }
        result.sort((a, b) -> {
            OffsetDateTime ca = (OffsetDateTime) a.get("createdAt");
            OffsetDateTime cb = (OffsetDateTime) b.get("createdAt");
            if (ca == null || cb == null) {
                return 0;
            }
            return cb.compareTo(ca);
        });
        return result;
    }

    // ------------------------------------------------------------------
    // get_ref_driver_payment_details
    // ------------------------------------------------------------------

    public JsonNode getRefDriverPaymentDetails(UUID ownerDriverId, UUID refDriverId) {
        Driver refDriver = driverRepository.findById(refDriverId)
                .orElseThrow(() -> new ApiException(404, "Driver not found"));

        if (refDriver.getReferredBy() == null || !refDriver.getReferredBy().getId().equals(ownerDriverId)) {
            throw new ApiException(403, "This driver is not in your referrals list");
        }

        JsonNode paymentRes = internalServiceClient.post(rideServiceUrl + "/ref-driver-ride-payments",
                Map.of("ownerDriverId", ownerDriverId.toString(), "refDriverId", refDriverId.toString()));
        if (!paymentRes.path("status").asBoolean(false)) {
            throw new ApiException(paymentRes.path("statusCode").asInt(500),
                    textOrDefault(paymentRes, "message", "Failed to fetch referral driver payment details"));
        }
        return paymentRes.path("data");
    }

    // ------------------------------------------------------------------
    // get_driver_dashboard_stats
    // ------------------------------------------------------------------

    public Map<String, Object> getDriverDashboardStats(UUID driverId) {
        JsonNode summaryRes = internalServiceClient.post(rideServiceUrl + "/driver-ride-summary/" + driverId, null);
        if (!summaryRes.path("status").asBoolean(false)) {
            throw new ApiException(500, textOrDefault(summaryRes, "message", "Failed to fetch earning summary"));
        }

        JsonNode outstationRes = internalServiceClient.post(rideServiceUrl + "/outstation-feeds",
                Map.of("driverId", driverId.toString(), "getCount", true));
        if (!outstationRes.path("status").asBoolean(false)) {
            throw new ApiException(500, textOrDefault(outstationRes, "message", "Failed to fetch outstation ride count"));
        }

        JsonNode summaryArray = summaryRes.path("data").path("summary");
        Map<String, Object> summary = summaryArray.isArray() && summaryArray.size() > 0
                ? internalServiceClient.asMap(summaryArray.get(0))
                : new LinkedHashMap<>();

        long outstationRideCount = outstationRes.path("data").path("totalRides").asLong(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("outstationRideCount", outstationRideCount);
        return result;
    }

    // ------------------------------------------------------------------
    // get_drivers_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getDriversReport() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime fromDate = now.minusDays(7);

        long totalDrivers = driverRepository.count(deletedEq(false));
        long deletedDrivers = driverRepository.count(deletedEq(true));
        long newDrivers = driverRepository.count(Specification.where(deletedEq(false))
                .and((root, query, cb) -> cb.between(root.get("createdAt"), fromDate, now)));
        long activeDrivers = driverRepository.countActiveDrivers();
        long onlineDrivers = driverRepository.count(driverStatusEq("online"));
        long onTripDrivers = driverRepository.count(driverStatusEq("on_trip"));
        long kycCompletedDrivers = driverRepository.count(kycCompletedEq(true));
        long kycPendingDrivers = driverRepository.count(kycStatusEq("pending"));
        long kycRejectedDrivers = driverRepository.count(kycStatusEq("rejected"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDrivers", totalDrivers);
        result.put("deletedDrivers", deletedDrivers);
        result.put("newDrivers", newDrivers);
        result.put("activeDrivers", activeDrivers);
        result.put("onlineDrivers", onlineDrivers);
        result.put("onTripDrivers", onTripDrivers);
        result.put("kycCompletedDrivers", kycCompletedDrivers);
        result.put("kycPendingDrivers", kycPendingDrivers);
        result.put("kycRejectedDrivers", kycRejectedDrivers);
        return result;
    }

    // ------------------------------------------------------------------
    // get_drivers_ride_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getDriversRideReport(GetDriversRideReportRequest request) {
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 10;

        Specification<Driver> spec = Specification.where(null);

        String searchText = request.getSearchText() == null ? null : request.getSearchText().trim();
        if (searchText != null && !searchText.isEmpty()) {
            spec = spec.and(DriverSpecifications.searchText(searchText));
        }
        if (Boolean.TRUE.equals(request.getKycCompleted()) || Boolean.FALSE.equals(request.getKycCompleted())) {
            spec = spec.and(DriverSpecifications.kycCompletedEq(request.getKycCompleted()));
        }
        if (request.getKycStatus() != null && !request.getKycStatus().isBlank()) {
            spec = spec.and(DriverSpecifications.kycStatusEq(request.getKycStatus()));
        }
        if (request.getDriverStatus() != null && !request.getDriverStatus().isBlank()) {
            spec = spec.and(DriverSpecifications.driverStatusEq(request.getDriverStatus()));
        }
        if (request.getGenderFilter() != null && !request.getGenderFilter().isBlank()) {
            spec = spec.and(DriverSpecifications.genderEq(request.getGenderFilter()));
        }
        if (request.getBlockFilter() != null) {
            spec = spec.and(DriverSpecifications.deletedEq(request.getBlockFilter()));
        }

        Sort sort = (request.getSort() != null && request.getSort() != 0)
                ? Sort.by(request.getSort() > 0 ? Sort.Direction.ASC : Sort.Direction.DESC, "firstName")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                : Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageable = PageRequest.of(page - 1, limit, sort);
        var pageResult = driverRepository.findAll(spec, pageable);

        List<Map<String, Object>> list = pageResult.getContent().stream()
                .map(this::toRideReportMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotalElements());
        return result;
    }

    private Map<String, Object> toRideReportMap(Driver d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_id", d.getId());
        map.put("firstName", d.getFirstName());
        map.put("lastName", d.getLastName());
        map.put("mobile", d.getMobile());
        map.put("email", d.getEmail());
        map.put("city", d.getCity());
        map.put("state", d.getState());
        map.put("gender", d.getGender());
        Map<String, Object> bankDetails = new LinkedHashMap<>();
        bankDetails.put("accountNumber", d.getBankAccountNumber());
        bankDetails.put("ifsc", d.getBankIfsc());
        bankDetails.put("holderName", d.getBankHolderName());
        bankDetails.put("bankName", d.getBankName());
        bankDetails.put("branch", d.getBankBranch());
        bankDetails.put("upiId", d.getBankUpiId());
        bankDetails.put("upiQrImage", d.getBankUpiQrImage());
        map.put("bankDetails", bankDetails);
        map.put("delete", d.isDeleted());
        map.put("driverStatus", d.getDriverStatus());
        map.put("kycCompleted", d.isKycCompleted());
        map.put("kycStatus", d.getKycStatus());
        map.put("unverifiedDriver", d.isUnverifiedDriver());
        map.put("vehicleNumber", d.getVehicleNumber());
        map.put("vehicleType", d.getVehicleType());
        map.put("vehicleVariant", d.getVehicleVariant());
        map.put("socketOnline", d.isSocketOnline());
        map.put("onRide", d.isOnRide());
        map.put("createdAt", d.getCreatedAt());
        return map;
    }

    // ------------------------------------------------------------------
    // update_referral
    // ------------------------------------------------------------------

    @Transactional
    public void updateReferral(UUID driverId, String referralCode) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ApiException(422, "Driver id is not valid"));

        Driver refDriver = driverRepository.findByReferralCode(referralCode).orElse(null);
        if (refDriver == null) {
            throw new ApiException(422, "Referral code is not valid");
        }
        if (refDriver.getId().equals(driverId)) {
            throw new ApiException(422, "You cannot refer yourself");
        }
        if (driver.getReferredBy() != null) {
            throw new ApiException(422, "Driver already referred");
        }

        UUID referrerId = driverReferralRewardService.applyReferralReward(driverId, referralCode);
        if (referrerId == null) {
            throw new ApiException(422, "Driver already referred");
        }
        // Legacy also fires a best-effort push notification to the referrer here
        // (sendNotification({status:"referred", ...})); no notification client exists yet in
        // driver-service to reproduce that side effect.
    }

    // ------------------------------------------------------------------
    // export_report
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DriverExportReportResult exportReport(String fileType, String search, String docStatus,
            String driverStatus, String genderFilter, String blockFilter, String nameSorting) {
        Specification<Driver> spec = Specification.where(null);

        String searchValue = search == null ? null : search.trim();
        if (searchValue != null && !searchValue.isEmpty()) {
            spec = spec.and(DriverSpecifications.searchText(searchValue));
        }
        if (docStatus != null && !docStatus.isBlank()) {
            spec = spec.and(DriverSpecifications.kycStatusEq(docStatus));
        }
        if (driverStatus != null && !driverStatus.isBlank()) {
            spec = spec.and(DriverSpecifications.driverStatusEq(driverStatus));
        }
        if (genderFilter != null && !genderFilter.isBlank()) {
            spec = spec.and(DriverSpecifications.genderEq(genderFilter));
        }
        if (blockFilter != null && !blockFilter.isBlank()) {
            spec = spec.and(DriverSpecifications.deletedEq("true".equalsIgnoreCase(blockFilter)));
        }

        Sort.Direction direction = "-1".equals(nameSorting) ? Sort.Direction.DESC : Sort.Direction.ASC;
        List<Driver> drivers = driverRepository.findAll(spec, Sort.by(direction, "firstName"));

        List<Map<String, Object>> rows = buildExportRows(drivers);
        if (rows.isEmpty()) {
            return DriverExportReportResult.error("No data to export");
        }

        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        try {
            if ("csv".equals(fileType)) {
                return DriverExportReportResult.of(buildCsv(rows, columns), "text/csv", "drivers.csv");
            }
            if ("pdf".equals(fileType)) {
                return DriverExportReportResult.of(buildPdf(rows, columns), "application/pdf", "drivers.pdf");
            }
            if ("excel".equals(fileType)) {
                return DriverExportReportResult.of(buildExcel(rows, columns),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "drivers.xlsx");
            }
        } catch (IOException e) {
            throw new ApiException(500, "Failed to generate export file");
        }
        return DriverExportReportResult.error("Invalid fileType");
    }

    private List<Map<String, Object>> buildExportRows(List<Driver> drivers) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Driver d : drivers) {
            Map<String, Object> row = new LinkedHashMap<>();
            String name = joinNonBlank(" ", d.getFirstName(), d.getLastName());
            row.put("Driver Name", name.isEmpty() ? "-" : name);
            row.put("Email", blankToDash(d.getEmail()));
            row.put("Gender", blankToDash(d.getGender()));
            row.put("Mobile Number", blankToDash(d.getMobile()));
            row.put("City", blankToDash(d.getCity()));
            row.put("Register At", d.getCreatedAt() == null ? "-" : formatter.format(d.getCreatedAt()));
            row.put("Document Status", blankToDash(d.getKycStatus()));
            row.put("Driver Status", blankToDash(d.getDriverStatus()));
            row.put("Block Status", d.isDeleted() ? "Blocked" : "Active");
            rows.add(row);
        }
        return rows;
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(delimiter, parts);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private byte[] buildCsv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columns));
        for (Map<String, Object> row : rows) {
            sb.append("\n");
            String line = columns.stream()
                    .map(col -> "\"" + String.valueOf(row.getOrDefault(col, "")) + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            sb.append(line);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildExcel(List<Map<String, Object>> rows, List<String> columns) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Drivers");

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 25 * 256);
            }

            int rowIndex = 1;
            for (Map<String, Object> row : rows) {
                Row xlsxRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    xlsxRow.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdf(List<Map<String, Object>> rows, List<String> columns) throws IOException {
        PDRectangle landscapeA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float margin = 40f;
        float rowHeight = 20f;

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(landscapeA4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float pageContentWidth = landscapeA4.getWidth() - 2 * margin;
            float colWidth = pageContentWidth / columns.size();
            float y = landscapeA4.getHeight() - margin;

            y = drawPdfTitle(contentStream, landscapeA4, y);
            y = drawPdfRow(contentStream, columns, null, margin, y, colWidth, rowHeight, PDF_HEADER_FONT, 8);

            for (Map<String, Object> row : rows) {
                if (y - rowHeight < margin) {
                    contentStream.close();
                    page = new PDPage(landscapeA4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = landscapeA4.getHeight() - margin;
                    y = drawPdfRow(contentStream, columns, null, margin, y, colWidth, rowHeight, PDF_HEADER_FONT, 8);
                }
                y = drawPdfRow(contentStream, columns, row, margin, y, colWidth, rowHeight, PDF_BODY_FONT, 7);
            }

            contentStream.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private float drawPdfTitle(PDPageContentStream contentStream, PDRectangle page, float y) throws IOException {
        String title = "Driver Report";
        float fontSize = 18f;
        float textWidth = PDF_TITLE_FONT.getStringWidth(title) / 1000 * fontSize;
        contentStream.beginText();
        contentStream.setFont(PDF_TITLE_FONT, fontSize);
        contentStream.newLineAtOffset((page.getWidth() - textWidth) / 2, y);
        contentStream.showText(title);
        contentStream.endText();
        return y - 30f;
    }

    private float drawPdfRow(PDPageContentStream contentStream, List<String> columns, Map<String, Object> row,
            float margin, float y, float colWidth, float rowHeight, PDFont font, float fontSize) throws IOException {
        float x = margin;
        for (String col : columns) {
            String text = row == null ? col : String.valueOf(row.getOrDefault(col, ""));
            drawCenteredText(contentStream, text, x, y, colWidth, font, fontSize);
            x += colWidth;
        }
        return y - rowHeight;
    }

    private void drawCenteredText(PDPageContentStream contentStream, String text, float x, float y, float width,
            PDFont font, float fontSize) throws IOException {
        String safeText = text == null ? "" : text.replaceAll("[\\r\\n]", " ");
        if (safeText.length() > 60) {
            safeText = safeText.substring(0, 60);
        }
        float textWidth = font.getStringWidth(safeText) / 1000 * fontSize;
        float offset = Math.max((width - textWidth) / 2, 0);
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x + offset, y);
        contentStream.showText(safeText);
        contentStream.endText();
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private Specification<Driver> deletedEq(boolean deleted) {
        return (root, query, cb) -> cb.equal(root.get("deleted"), deleted);
    }

    private Specification<Driver> driverStatusEq(String status) {
        return (root, query, cb) -> cb.equal(root.get("driverStatus"), status);
    }

    private Specification<Driver> kycCompletedEq(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("kycCompleted"), value);
    }

    private Specification<Driver> kycStatusEq(String status) {
        return (root, query, cb) -> cb.equal(root.get("kycStatus"), status);
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        if (node == null) {
            return def;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? def : value.asText(def);
    }
}
