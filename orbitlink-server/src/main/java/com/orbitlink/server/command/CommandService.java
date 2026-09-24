package com.orbitlink.server.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and logs command submissions.
 *
 * <p>Every attempt is recorded, including rejections. In flight operations the
 * question after an anomaly is "what was sent, by whom, and what was refused" —
 * discarding failed attempts throws away the most interesting rows.
 *
 * <p>This phase stops at validate-and-log. Actually transmitting the command
 * would mean an uplink socket and an acknowledgement protocol, which is a
 * separate problem from deciding whether a command is well-formed.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final CommandDefinitionRepository definitionRepository;
    private final CommandLogRepository logRepository;
    private final CommandValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommandService(CommandDefinitionRepository definitionRepository,
                          CommandLogRepository logRepository,
                          CommandValidator validator) {
        this.definitionRepository = definitionRepository;
        this.logRepository = logRepository;
        this.validator = validator;
    }

    /** @param confirmed operator acknowledgement, required for a hazardous command */
    public record Submission(
            String mnemonic, Map<String, Object> arguments, String issuedBy, boolean confirmed) {
    }

    public record Outcome(boolean accepted, String mnemonic, List<String> errors, Long logId) {
    }

    @Transactional
    public Outcome submit(Submission submission) {
        String mnemonic = submission.mnemonic();
        String issuedBy = submission.issuedBy() == null ? "unknown" : submission.issuedBy();
        String argumentsJson = toJson(submission.arguments());

        Optional<CommandDefinition> found =
                definitionRepository.findByMnemonicAndDictionaryActiveIsTrue(mnemonic);

        if (found.isEmpty()) {
            return reject(null, mnemonic, issuedBy, argumentsJson,
                    List.of("unknown command \"" + mnemonic + "\""));
        }

        CommandDefinition command = found.get();

        // Hazardous commands need an explicit acknowledgement. Checked before
        // argument validation so the operator is told the command is dangerous
        // even if they also got an argument wrong.
        if (command.isHazardous() && !submission.confirmed()) {
            return reject(command, mnemonic, issuedBy, argumentsJson,
                    List.of("command \"" + mnemonic
                            + "\" is hazardous and requires confirmation"));
        }

        CommandValidator.Result result = validator.validate(command, submission.arguments());
        if (!result.valid()) {
            return reject(command, mnemonic, issuedBy, argumentsJson, result.errors());
        }

        CommandLog accepted = logRepository.save(new CommandLog(
                command, mnemonic, issuedBy, argumentsJson, CommandLog.Status.ACCEPTED, null));

        log.info("Command accepted: {} by {} args={}", mnemonic, issuedBy, argumentsJson);
        return new Outcome(true, mnemonic, List.of(), accepted.getId());
    }

    private Outcome reject(CommandDefinition command, String mnemonic, String issuedBy,
                           String argumentsJson, List<String> errors) {
        String reason = String.join("; ", errors);
        CommandLog rejected = logRepository.save(new CommandLog(
                command, mnemonic, issuedBy, argumentsJson, CommandLog.Status.REJECTED, reason));

        log.warn("Command rejected: {} by {} reason={}", mnemonic, issuedBy, reason);
        return new Outcome(false, mnemonic, List.copyOf(errors), rejected.getId());
    }

    private String toJson(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (JsonProcessingException e) {
            // Falling back to an empty object keeps the audit row writable; a
            // command must never be lost from the log because its arguments
            // would not serialise.
            log.warn("Could not serialise command arguments", e);
            return "{}";
        }
    }
}
