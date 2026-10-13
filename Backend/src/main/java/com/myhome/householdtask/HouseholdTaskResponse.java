package com.myhome.householdtask;

import java.time.LocalDate;

public record HouseholdTaskResponse (
        Long id,
        String name,
        String taskType,
        Integer cycleDays,
        LocalDate nextDueDate,
        Boolean active,
        String memo
) {
}
