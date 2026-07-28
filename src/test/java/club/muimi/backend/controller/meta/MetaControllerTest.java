package club.muimi.backend.controller.meta;

import club.muimi.backend.common.enums.PeriodType;
import club.muimi.backend.service.period.PeriodService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerTest {

    @Test
    void getCurrentPeriodShouldUseApplicationClockForServerTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-27T02:30:00Z"), ZoneId.of("Asia/Shanghai"));
        PeriodService periodService = mock(PeriodService.class);
        when(periodService.getCurrentPeriod()).thenReturn(PeriodType.SELECTION);
        MetaController controller = new MetaController(periodService, clock);

        var response = controller.getCurrentPeriod();

        assertThat(response.data().currentPeriod()).isEqualTo(PeriodType.SELECTION);
        assertThat(response.data().serverTime().toString()).isEqualTo("2026-06-27T10:30+08:00");
    }
}
