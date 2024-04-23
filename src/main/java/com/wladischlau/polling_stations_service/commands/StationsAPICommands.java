package com.wladischlau.polling_stations_service.commands;

import com.wladischlau.polling_stations_service.service.ITextService;
import com.wladischlau.polling_stations_service.service.StationService;
import com.wladischlau.service.model.StationInfo;
import com.wladischlau.service.model.StationList;
import com.wladischlau.service.model.StationListRegionsInner;
import jakarta.validation.constraints.Positive;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

// Utility conventions https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html
@ShellComponent
public class StationsAPICommands {

    private final StationService stationService;
    private final ITextService textService;

    public StationsAPICommands(
            StationService stationService,
            ITextService textService
    ) {
        this.stationService = stationService;
        this.textService = textService;
    }

    @ShellMethod(key = "stations", value = "Get all stations listed and sorted by specified parameters.")
    public String getStations(
            @ShellOption(value = {"--region-codes", "-r"}, defaultValue = ShellOption.NULL, arity = 1) String regionCodes,
            @ShellOption(value = {"--station-numbers", "-s"}, defaultValue = ShellOption.NULL, arity = 1) String stationNumbers
    ) {
        StationList foundStations;
        if (regionCodes != null && !regionCodes.isBlank()) {
            foundStations = stationService.getAllStationsInRegions(regionCodes);
        } else if (stationNumbers != null && !stationNumbers.isBlank()) {
            foundStations = stationService.getAllStationsByNumbers(stationNumbers);
        } else {
            foundStations = stationService.getAllStations();
        }
        if (foundStations.getRegions().isEmpty()) {
            return "No stations found by specified parameters.";
        }
        AtomicInteger counter = new AtomicInteger();
        foundStations.getRegions().forEach(region -> {
            System.out.println("Region " + region.getCode() + ":\n");
            region.getStations().forEach(station -> {
                counter.getAndIncrement();
                System.out.println("  " + StationService.getStationBriefInfoString(station));
            });
        });
        return "Stations found by specified parameters: " + counter + ".";
    }

    @ShellMethod(key = "station_info", value = "Get polling station's full information.")
    public String getStationInfo(
            @ShellOption(value = {"--id"}, defaultValue = ShellOption.NULL, arity = 1) @Positive Long id,
            @ShellOption(value = {"--border-address", "-b"}, defaultValue = ShellOption.NULL, arity = 5) String[] borderAddress,
            @ShellOption(value = {"--region-code", "-r"}, defaultValue = ShellOption.NULL, arity = 1) String regionCode,
            @ShellOption(value = {"--station-number", "-s"}, defaultValue = ShellOption.NULL, arity = 1) @Positive Integer stationNumber,
            @ShellOption(value = {"--by-date-exclusive", "-d"}, defaultValue = ShellOption.NULL, arity = 1) String date
    ) {
        StationInfo stationInfo;
        // omit region and station number if id is specified:
        if (id != null) {
            stationInfo = stationService
                    .getStationInfoByStationId(id, date);
        } else if (borderAddress != null) {
            stationInfo = stationService
                    .getStationInfoByBorderAddress(borderAddress, date);
        } else {
            stationInfo = stationService
                    .getStationInfoByRegionCodeAndStationNumber(regionCode, stationNumber, date);
        }
        return StationService.getStationFullInfoString(stationInfo);
    }

    @ShellMethod(key = "report", value = "Generate txt report about the attendance for the specified stations.")
    public void generateReport(
            @ShellOption(value = {"--region-codes", "-r"}, defaultValue = ShellOption.NULL, arity = 1) String regionCodes,
            @ShellOption(value = {"--station-numbers", "-s"}, defaultValue = ShellOption.NULL, arity = 1) String stationNumbers,
            @ShellOption(value = {"--by-date-exclusive", "-d"}, defaultValue = ShellOption.NULL, arity = 1) String date,
            @ShellOption(value = {"--path", "-p"}, defaultValue = "./data/report.txt", arity = 1) String path
    ) {
        StationList foundStations;
        StringBuilder result = new StringBuilder();
        if (regionCodes != null && !regionCodes.isBlank()) {
            foundStations = stationService.getAllStationsInRegions(regionCodes);
        } else if (stationNumbers != null && !stationNumbers.isBlank()) {
            foundStations = stationService.getAllStationsByNumbers(stationNumbers);
        } else {
            foundStations = stationService.getAllStations();
        }
        if (foundStations.getRegions().isEmpty()) {
            System.out.println("No stations found by specified parameters.");
            return;
        }
        for (StationListRegionsInner region : foundStations.getRegions()) {
            result.append("Report for region: " + region.getCode() + "\n");
            region.getStations()
                    .stream()
                    .map(el -> stationService.getStationInfoByStationId(el.getId(), date))
                    .forEach(el -> {
                        if (el.getAttendance() == null) {
                            System.out.println("No attendance data for station " + el.getPsNumber());
                        }
                        var attendanceStats = stationService
                                .calculateAttendanceStats(
                                        el.getAttendance(),
                                        el.getOpensAtHour(),
                                        el.getClosesAtHour()
                                );
                        result.append(
                                String.format("""
                                                Polling station %d basic information:
                                                    Hotline: %s
                                                    Opening hour: %d
                                                    Closing hour: %d
                                                    Attendance information:
                                                        Total attendance: %d // by provided date, if there was one
                                                        Total days counted: %d
                                                        The busiest hour: %d
                                                        Attendance at the busiest hour: %d
                                                        The LEAST busy hour: %d
                                                        Attendance at the LEAST busy hour: %d
                                                        AVG attendance by hour: %d
                                                """,
                                        el.getPsNumber(),
                                        el.getHotline(),
                                        el.getOpensAtHour(),
                                        el.getClosesAtHour(),
                                        attendanceStats.getTotalByDate(),
                                        attendanceStats.getDaysCount(),
                                        attendanceStats.getMaxLoadHour(),
                                        attendanceStats.getMaxLoadHourAttendance(),
                                        attendanceStats.getMinLoadHour(),
                                        attendanceStats.getMinLoadHourAttendance(),
                                        attendanceStats.getAvgLoadByHour()
                                )
                        );
                    });
        }
        try {
            textService.writeToFile(path, result.toString());
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
            return;
        }
        System.out.print("File is created successfully with the content.\n");
    }
}
