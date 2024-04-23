package com.wladischlau.polling_stations_service.model;

import lombok.Data;

@Data
public final class AttendanceStats {
    private Long totalByDate = 0L;
    private Integer daysCount = 0;
    private Integer maxLoadHour = 0;
    private Long maxLoadHourAttendance = Long.MIN_VALUE;
    private Integer minLoadHour = 0;
    private Long minLoadHourAttendance = Long.MAX_VALUE;
    private Long avgLoadByHour = 0L;
}
