package com.orbitlink.server.command;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Command definitions, submission, and the audit log. */
@RestController
@RequestMapping("/api/v1/commands")
@Validated
public class CommandController {

    private final CommandService commandService;
    private final CommandDefinitionRepository definitionRepository;
    private final CommandLogRepository logRepository;

    public CommandController(CommandService commandService,
                             CommandDefinitionRepository definitionRepository,
                             CommandLogRepository logRepository) {
        this.commandService = commandService;
        this.definitionRepository = definitionRepository;
        this.logRepository = logRepository;
    }

    public record ArgumentView(
            String name, String description, ArgumentDataType dataType, int position,
            Double minValue, Double maxValue, List<String> allowedValues, boolean required) {
    }

    public record CommandView(
            String mnemonic, String name, String description,
            int apid, int functionCode, boolean hazardous, List<ArgumentView> arguments) {
    }

    public record SubmitRequest(
            @NotBlank String mnemonic,
            Map<String, Object> arguments,
            String issuedBy,
            boolean confirmed) {
    }

    public record LogView(
            Long id, String mnemonic, Instant issuedAt, String issuedBy,
            String arguments, CommandLog.Status status, String rejectionReason) {
    }

    /** Every command the active dictionary defines. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<CommandView> list() {
        return definitionRepository.findByDictionaryActiveIsTrueOrderByMnemonic().stream()
                .map(CommandController::toView)
                .toList();
    }

    /**
     * Submits a command for validation and logging.
     *
     * <p>A rejected command answers 422, not 400: the request was well-formed
     * and understood, its contents were just not acceptable. The same
     * distinction the dictionary endpoint makes.
     */
    @PostMapping
    public ResponseEntity<CommandService.Outcome> submit(@RequestBody SubmitRequest request) {
        CommandService.Outcome outcome = commandService.submit(new CommandService.Submission(
                request.mnemonic(), request.arguments(), request.issuedBy(), request.confirmed()));

        return outcome.accepted()
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome)
                : ResponseEntity.unprocessableEntity().body(outcome);
    }

    @GetMapping("/log")
    @Transactional(readOnly = true)
    public List<LogView> log(
            @RequestParam(required = false) CommandLog.Status status,
            @RequestParam(defaultValue = "50") int limit) {

        PageRequest page = PageRequest.of(0, Math.min(limit, 500));
        List<CommandLog> entries = status == null
                ? logRepository.findAllByOrderByIssuedAtDesc(page)
                : logRepository.findByStatusOrderByIssuedAtDesc(status, page);

        return entries.stream()
                .map(entry -> new LogView(
                        entry.getId(), entry.getMnemonic(), entry.getIssuedAt(),
                        entry.getIssuedBy(), entry.getArguments(),
                        entry.getStatus(), entry.getRejectionReason()))
                .toList();
    }

    private static CommandView toView(CommandDefinition command) {
        List<ArgumentView> arguments = command.getArguments().stream()
                .map(argument -> new ArgumentView(
                        argument.getName(), argument.getDescription(), argument.getDataType(),
                        argument.getPosition(), argument.getMinValue(), argument.getMaxValue(),
                        argument.allowedValueList(), argument.isRequired()))
                .toList();

        return new CommandView(
                command.getMnemonic(), command.getName(), command.getDescription(),
                command.getApid(), command.getFunctionCode(), command.isHazardous(), arguments);
    }
}
