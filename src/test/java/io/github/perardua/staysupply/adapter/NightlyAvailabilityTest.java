package io.github.perardua.staysupply.adapter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NightlyAvailabilityTest {

    private static final LocalDate FIRST_NIGHT = LocalDate.of(2026, 9, 1);

    /** 첫날부터 하루씩 이어지는 날짜별 잔여 객실 수. */
    private static Map<LocalDate, Integer> remaining(int... remainingPerNight) {
        Map<LocalDate, Integer> remainingByDate = new HashMap<>();
        for (int i = 0; i < remainingPerNight.length; i++) {
            remainingByDate.put(FIRST_NIGHT.plusDays(i), remainingPerNight[i]);
        }
        return remainingByDate;
    }

    private static AvailabilityQuery nights(int nights) {
        return new AvailabilityQuery(FIRST_NIGHT, FIRST_NIGHT.plusDays(nights), 2, 0);
    }

    @Test
    void 예약_가능_객실_수는_기간_중_날짜별_잔여의_최솟값이다() {
        assertThat(NightlyAvailability.availableRooms(remaining(3, 1, 5), nights(3))).isEqualTo(1);
    }

    @Test
    void 기간_가운데_날짜의_잔여가_0이면_예약_가능_객실_수는_0이다() {
        assertThat(NightlyAvailability.availableRooms(remaining(3, 0, 5), nights(3))).isZero();
    }

    @Test
    void 기간_첫_날짜의_잔여가_0이면_예약_가능_객실_수는_0이다() {
        assertThat(NightlyAvailability.availableRooms(remaining(0, 3, 5), nights(3))).isZero();
    }

    @Test
    void 기간_마지막_날짜의_잔여가_0이면_예약_가능_객실_수는_0이다() {
        assertThat(NightlyAvailability.availableRooms(remaining(5, 3, 0), nights(3))).isZero();
    }

    @Test
    void 숙박이_하루면_그날의_잔여가_그대로_예약_가능_객실_수가_된다() {
        assertThat(NightlyAvailability.availableRooms(remaining(4), nights(1))).isEqualTo(4);
    }

    @Test
    void 요청_기간의_날짜가_하나라도_빠져_있으면_예약_가능_객실_수는_0이다() {
        assertThat(NightlyAvailability.availableRooms(remaining(3, 1), nights(3))).isZero();
    }

    @Test
    void 요청_기간_밖의_날짜는_예약_가능_객실_수_판정에서_제외된다() {
        Map<LocalDate, Integer> remainingByDate = remaining(3, 1, 5);
        remainingByDate.put(FIRST_NIGHT.minusDays(1), 0);
        remainingByDate.put(FIRST_NIGHT.plusDays(3), 0);

        assertThat(NightlyAvailability.availableRooms(remainingByDate, nights(3))).isEqualTo(1);
    }
}
