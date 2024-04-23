package com.wladischlau.polling_stations_service.service;

import com.wladischlau.polling_stations_service.model.AttendanceStats;
import com.wladischlau.service.api.BriefStationInfoApi;
import com.wladischlau.service.api.FullStationInfoApi;
import com.wladischlau.service.model.*;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.summingLong;

@Service
public class StationService {

    private final BriefStationInfoApi briefStationInfoApiClient;
    private final FullStationInfoApi fullStationInfoApiClient;

    public StationService() {
        this.fullStationInfoApiClient = new FullStationInfoApi();
        this.briefStationInfoApiClient = new BriefStationInfoApi();
    }

    public StationList getAllStations() {
        return briefStationInfoApiClient.getStations().block();
    }

    public StationList getAllStationsInRegions(String regions) {
        return briefStationInfoApiClient
                .getStationsFiltered(
                        Arrays.stream(regions.split(",")).toList(), List.of())
                .block();
    }

    public StationList getAllStationsByNumbers(String stationNumbers) {
        return briefStationInfoApiClient
                .getStationsFiltered(
                        List.of(),
                        Arrays
                                .stream(stationNumbers.split(","))
                                .map(Integer::valueOf)
                                .toList()
                )
                .block();
    }

    public StationInfo getStationInfoByStationId(
            Long stationId,
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date
    ) {
        return fullStationInfoApiClient
                .getStationInfoById(stationId, parseDate(date)).block();
    }

    public StationInfo getStationInfoByRegionCodeAndStationNumber(
            String regionCode,
            Integer stationNumber,
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date
    ) {
        return fullStationInfoApiClient
                .getStationInfoByRegionCodeAndStationNumber(
                        regionCode,
                        stationNumber,
                        parseDate(date)
                ).block();
    }

    public StationInfo getStationInfoByBorderAddress(
            String[] addressValue,
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date
    ) {
        if (addressValue.length < 4) {
            System.err.println(
                    "Invalid number of address value parameters." +
                    "Provided " + addressValue.length + ", at least 4 required."
            );
        }
        var address = new Address()
                .regionCode(addressValue[0])
                .city(addressValue[1])
                .street(addressValue[2])
                .houseNumber(addressValue[3]);

        if (addressValue.length > 4) {
            address.building(addressValue[4]);
        }

        return fullStationInfoApiClient
                .getStationInfoByBorderAddress(
                        address,
                        parseDate(date)
                ).block();
    }

    public AttendanceStats calculateAttendanceStats(
            List<Attendance> attendances,
            Integer openingHour,
            Integer closingHour
    ) {
        AttendanceStats res = new AttendanceStats();
        var totalHoursPerDay = closingHour - openingHour;
        res.setDaysCount(attendances.size() / totalHoursPerDay);

        var totalAttendancesPerHour = attendances
                .stream()
                .collect(
                        Collectors.groupingBy(
                                Attendance::getHour,
                                summingLong(Attendance::getAttendance)
                        )
                );

        Long totalAttendance = 0L;
        for (Map.Entry<Integer, Long> entry : totalAttendancesPerHour.entrySet()) {
            if (entry.getValue() > res.getMaxLoadHourAttendance()) {
                res.setMaxLoadHour(entry.getKey());
                res.setMaxLoadHourAttendance(entry.getValue());
            }
            if (entry.getValue() < res.getMinLoadHourAttendance()) {
                res.setMinLoadHour(entry.getKey());
                res.setMinLoadHourAttendance(entry.getValue());
            }
            totalAttendance += entry.getValue();
        }

        res.setTotalByDate(totalAttendance);
        res.setAvgLoadByHour(totalAttendance / ((long) res.getDaysCount() * totalHoursPerDay));

        return res;
    }

    private static LocalDate parseDate(String date) {
        if (date == null) {
            return LocalDate.now();
        }
        return LocalDate.parse(date);
    }

    public static String getStationBriefInfoString(
            StationInfoBrief stationInfoBrief
    ) {
        return String.format("""
                        Polling station: %s
                          Internal id: %s
                          Station address: \n%s
                        """,
                stationInfoBrief.getPsNumber(),
                stationInfoBrief.getId(),
                getAddressString(stationInfoBrief.getActualAddress())
        );
    }

    public static String getStationFullInfoString(
            StationInfo stationInfo
    ) {
        return stationInfo.toString();
    }

    public static String getAddressString(Address address) {
        return String.format("""
                        \tCity: %s,
                        \tStreet: %s,
                        \tHouse number: %s,
                        \tBuilding: %s
                        """,
                address.getCity(),
                address.getStreet(),
                address.getHouseNumber(),
                address.getBuilding() != null ? address.getBuilding() : "-"
        );
    }
}
