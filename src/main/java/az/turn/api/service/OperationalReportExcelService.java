package az.turn.api;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class OperationalReportExcelService {
    public byte[] create(OperationalAnalyticsDto report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            summary(workbook, report);
            rooms(workbook, report.rooms());
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Excel hesabatı yaradıla bilmədi.", exception);
        }
    }

    private void summary(Workbook workbook, OperationalAnalyticsDto report) {
        Sheet sheet = workbook.createSheet("Summary");
        String[][] values = {
                {"Metric", "Value"},
                {"From", report.from().toString()},
                {"To", report.to().toString()},
                {"Total people", String.valueOf(report.totalPeople())},
                {"Live queue entries", String.valueOf(report.liveQueueEntries())},
                {"Planned bookings", String.valueOf(report.plannedBookings())},
                {"Completed", String.valueOf(report.completed())},
                {"Cancelled", String.valueOf(report.cancelled())},
                {"Skipped", String.valueOf(report.skipped())},
                {"Removed", String.valueOf(report.removed())},
                {"Reset", String.valueOf(report.reset())},
                {"Guest participants", String.valueOf(report.guestParticipants())},
                {"Registered participants", String.valueOf(report.registeredParticipants())},
                {"Average estimated wait", String.valueOf(report.averageEstimatedWaitMinutes())},
                {"Maximum estimated wait", String.valueOf(report.maximumEstimatedWaitMinutes())},
                {"Busiest day", report.busiestDay() == null ? "" : report.busiestDay()},
                {"Busiest hour", report.busiestHour() == null ? "" : String.valueOf(report.busiestHour())}
        };
        for (int index = 0; index < values.length; index++) writeRow(sheet, index, List.of(values[index]));
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void rooms(Workbook workbook, List<RoomOperationalMetricDto> metrics) {
        Sheet sheet = workbook.createSheet("Rooms");
        writeRow(sheet, 0, List.of(
                "Room ID", "Room", "Branch", "Live", "Planned", "Completed", "Cancelled",
                "Skipped", "Removed", "Reset", "Guests", "Registered", "Capacity minutes"
        ));
        for (int index = 0; index < metrics.size(); index++) {
            RoomOperationalMetricDto value = metrics.get(index);
            writeRow(sheet, index + 1, List.of(
                    String.valueOf(value.roomId()), value.roomName(), value.branchName() == null ? "" : value.branchName(),
                    String.valueOf(value.liveEntries()), String.valueOf(value.plannedBookings()),
                    String.valueOf(value.completed()), String.valueOf(value.cancelled()), String.valueOf(value.skipped()),
                    String.valueOf(value.removed()), String.valueOf(value.reset()), String.valueOf(value.guestParticipants()),
                    String.valueOf(value.registeredParticipants()), String.valueOf(value.estimatedCapacityMinutes())
            ));
        }
        for (int column = 0; column < 13; column++) sheet.autoSizeColumn(column);
    }

    private void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.size(); column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(values.get(column));
        }
    }
}
