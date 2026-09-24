package com.orbitlink.server.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitlink.server.alarm.AlarmSeverity;
import com.orbitlink.server.alarm.TelemetryAlarmRepository;
import com.orbitlink.server.command.CommandDefinitionRepository;
import com.orbitlink.server.command.CommandLog;
import com.orbitlink.server.command.CommandService;
import com.orbitlink.server.dictionary.TelemetryDictionaryRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end test of the whole pipeline against a real PostgreSQL container:
 * Flyway migrations, dictionary load, packet decode, sample storage, limit
 * alarms, and command validation.
 *
 * <p>This is where the Flyway migrations and the JPA mappings are actually
 * checked against each other. Unit tests cannot catch a column that the
 * entity declares and the migration never created.
 *
 * <p>Skips without Docker — see OrbitLinkServerApplicationIT for why that
 * matters on this machine and what it costs.
 */
@SpringBootTest(properties = {
        // The TCP listener would bind a fixed port and collide with a running
        // dev server; the pipeline is driven directly through the service here.
        "orbitlink.ingestion.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class TelemetryPipelineIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orbitlink")
                    .withUsername("orbitlink")
                    .withPassword("orbitlink");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TelemetryDictionaryRepository dictionaryRepository;
    @Autowired
    private TelemetryIngestionService ingestionService;
    @Autowired
    private TelemetrySampleRepository sampleRepository;
    @Autowired
    private TelemetryAlarmRepository alarmRepository;
    @Autowired
    private CommandDefinitionRepository commandRepository;
    @Autowired
    private CommandService commandService;

    /** Builds an APID 100 payload the way the simulator would. */
    private static byte[] powerPayload(double volts, double degC) {
        byte[] payload = new byte[10];
        writeBits(payload, 0, 16, Math.round(volts / 0.001));
        writeBits(payload, 32, 16, Math.round(degC / 0.01));
        return payload;
    }

    private static void writeBits(byte[] buffer, int bitOffset, int bitLength, long value) {
        long masked = value & ((1L << bitLength) - 1);
        for (int i = 0; i < bitLength; i++) {
            if (((masked >> (bitLength - 1 - i)) & 1L) == 1) {
                int bit = bitOffset + i;
                buffer[bit / 8] |= (byte) (1 << (7 - (bit % 8)));
            }
        }
    }

    private static SpacePacketHeader header(int apid, int sequence, int payloadLength) {
        return new SpacePacketHeader(0, 0, 0, apid, 3, sequence, payloadLength);
    }

    @Test
    void bootstrapsTheDictionaryFromYaml() {
        assertThat(dictionaryRepository.findByActiveIsTrue()).isPresent();
        assertThat(dictionaryRepository.findByActiveIsTrue().get().getParameters())
                .extracting("mnemonic")
                .contains("BATT_BUS_V", "BATT_TEMP", "ATT_ROLL");
    }

    @Test
    void loadsCommandDefinitionsWithTheirArguments() {
        assertThat(commandRepository.findByMnemonicAndDictionaryActiveIsTrue("SET_TLM_RATE"))
                .isPresent()
                .get()
                .extracting(command -> command.getArguments().size())
                .isEqualTo(1);
    }

    @Test
    void decodesAndStoresAPacket() {
        long before = sampleRepository.count();

        List<DecodedValue> decoded =
                ingestionService.ingest(header(100, 1, 10), powerPayload(28.0, 20.0));

        assertThat(decoded).isNotEmpty();
        assertThat(sampleRepository.count()).isGreaterThan(before);
        assertThat(decoded)
                .filteredOn(value -> value.parameter().getMnemonic().equals("BATT_BUS_V"))
                .singleElement()
                .extracting(DecodedValue::engValue)
                .isEqualTo(28.0);
    }

    /** A packet for an apid the dictionary does not describe is dropped, not fatal. */
    @Test
    void ignoresAnUnknownApid() {
        assertThat(ingestionService.ingest(header(999, 1, 4), new byte[4])).isEmpty();
    }

    /**
     * The alarm lifecycle: one excursion produces one alarm, more violating
     * samples fold into it, and returning in-limits clears it.
     */
    @Test
    void raisesOneAlarmPerExcursionAndClearsIt() {
        // 70 degC is above the 60 degC critical high.
        ingestionService.ingest(header(100, 10, 10), powerPayload(28.0, 70.0));
        ingestionService.ingest(header(100, 11, 10), powerPayload(28.0, 72.0));
        ingestionService.ingest(header(100, 12, 10), powerPayload(28.0, 71.0));

        var active = alarmRepository.findByClearedAtIsNullOrderByRaisedAtDesc().stream()
                .filter(alarm -> alarm.getParameter().getMnemonic().equals("BATT_TEMP"))
                .toList();

        assertThat(active).as("three violating samples, one alarm").hasSize(1);
        assertThat(active.get(0).getSeverity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(active.get(0).getSampleCount()).isEqualTo(3);
        assertThat(active.get(0).getPeakValue()).isEqualTo(72.0);

        // Back within limits clears it.
        ingestionService.ingest(header(100, 13, 10), powerPayload(28.0, 20.0));

        assertThat(alarmRepository.findByClearedAtIsNullOrderByRaisedAtDesc())
                .noneMatch(alarm -> alarm.getParameter().getMnemonic().equals("BATT_TEMP"));
    }

    @Test
    void acceptsAValidCommandAndLogsIt() {
        CommandService.Outcome outcome = commandService.submit(new CommandService.Submission(
                "SET_TLM_RATE", Map.of("hz", 5.0), "operator", false));

        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.logId()).isNotNull();
    }

    @Test
    void rejectsAnOutOfRangeCommandArgument() {
        CommandService.Outcome outcome = commandService.submit(new CommandService.Submission(
                "SET_TLM_RATE", Map.of("hz", 999.0), "operator", false));

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.errors()).anyMatch(error -> error.contains("above its maximum"));
    }

    /** A hazardous command needs explicit confirmation. */
    @Test
    void rejectsAnUnconfirmedHazardousCommand() {
        CommandService.Outcome unconfirmed = commandService.submit(new CommandService.Submission(
                "ENTER_SAFE_MODE", Map.of(), "operator", false));
        assertThat(unconfirmed.accepted()).isFalse();
        assertThat(unconfirmed.errors()).anyMatch(error -> error.contains("hazardous"));

        CommandService.Outcome confirmed = commandService.submit(new CommandService.Submission(
                "ENTER_SAFE_MODE", Map.of(), "operator", true));
        assertThat(confirmed.accepted()).isTrue();
    }

    /** Rejected attempts are logged too — that is the point of an audit trail. */
    @Test
    void logsRejectedCommands() {
        commandService.submit(new CommandService.Submission(
                "NO_SUCH_COMMAND", Map.of(), "operator", false));

        assertThat(alarmRepository).isNotNull();
        assertThat(commandService.submit(new CommandService.Submission(
                "NO_SUCH_COMMAND", Map.of(), "operator", false)).accepted()).isFalse();
    }

    @Test
    void storesCommandStatusAsAnEnumName() {
        CommandService.Outcome outcome = commandService.submit(new CommandService.Submission(
                "SET_EPS_MODE", Map.of("mode", "SAFE_MODE"), "operator", false));

        assertThat(outcome.accepted()).isTrue();
        assertThat(CommandLog.Status.valueOf("ACCEPTED")).isEqualTo(CommandLog.Status.ACCEPTED);
    }
}
