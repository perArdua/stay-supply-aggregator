package io.github.perardua.staysupply.adapter;

import java.time.LocalDate;
import java.util.Map;

/**
 * 예약 가능 객실 수의 정의가 요청 기간 전체를 예약할 수 있는 객실 수이므로 기간 중 최솟값을 취한다.
 * 공급사마다 응답 형태는 다르지만 이 판정은 같아야 하므로 한 자리에 둔다.
 */
public final class NightlyAvailability {

    private NightlyAvailability() {
    }

    public static int availableRooms(Map<LocalDate, Integer> remainingByDate, AvailabilityQuery query) {
        int minimum = Integer.MAX_VALUE;
        for (LocalDate night = query.checkIn(); night.isBefore(query.checkOut()); night = night.plusDays(1)) {
            minimum = Math.min(minimum, remainingByDate.getOrDefault(night, 0));
        }

        return minimum;
    }
}
