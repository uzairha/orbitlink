package com.orbitlink.server.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.orbitlink.server.command.CommandArgument;
import com.orbitlink.server.command.CommandDefinition;
import com.orbitlink.server.dictionary.validation.DictionaryValidator;
import com.orbitlink.server.dictionary.validation.ValidationReport;
import com.orbitlink.server.dictionary.yaml.CommandDefinitionYaml;
import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import com.orbitlink.server.dictionary.yaml.EnumStateDefinition;
import com.orbitlink.server.dictionary.yaml.ParameterDefinition;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parses a telemetry dictionary YAML file and persists it as a new version.
 *
 * <p>Parsing and persisting are separate public operations on purpose.
 * Phase 3 needs to parse a candidate file and report on it <em>without</em>
 * writing anything, so {@link #parse} stands alone and {@link #load} is the
 * one that touches the database.
 */
@Service
public class DictionaryLoader {

    private static final Logger log = LoggerFactory.getLogger(DictionaryLoader.class);

    private final TelemetryDictionaryRepository dictionaryRepository;
    private final DictionaryValidator validator;
    private final ObjectMapper yamlMapper;

    public DictionaryLoader(TelemetryDictionaryRepository dictionaryRepository,
                            DictionaryValidator validator) {
        this.dictionaryRepository = dictionaryRepository;
        this.validator = validator;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .findAndRegisterModules();
    }

    /** Reads and parses YAML without persisting anything. */
    public DictionaryFile parse(InputStream yaml) throws IOException {
        DictionaryFile file = yamlMapper.readValue(yaml, DictionaryFile.class);
        if (file.version() == null || file.version().isBlank()) {
            throw new IllegalArgumentException("dictionary version is required");
        }
        if (file.parameters() == null || file.parameters().isEmpty()) {
            throw new IllegalArgumentException("dictionary must define at least one parameter");
        }
        return file;
    }

    /**
     * Persists a parsed dictionary as a new version.
     *
     * @param activate whether this version becomes the active one
     * @throws IllegalStateException if the version already exists — dictionary
     *         versions are immutable, so re-loading one is a mistake worth
     *         surfacing rather than silently overwriting decode rules that
     *         historical telemetry still depends on.
     */
    @Transactional
    public TelemetryDictionary load(DictionaryFile file, String sourceFile, boolean activate) {
        if (dictionaryRepository.existsByVersion(file.version())) {
            throw new IllegalStateException(
                    "dictionary version already loaded: " + file.version());
        }

        // Validate before persisting, never after. This is what lets the
        // database carry real constraints: anything that reaches it is already
        // known good. Warnings are logged but do not block.
        ValidationReport report = validator.validate(file);
        if (!report.valid()) {
            throw new InvalidDictionaryException(report);
        }
        report.findings().forEach(finding ->
                log.warn("Dictionary {} [{}] {}: {}",
                        file.version(), finding.rule(), finding.mnemonic(), finding.message()));

        TelemetryDictionary dictionary =
                new TelemetryDictionary(file.version(), file.description(), sourceFile);

        for (ParameterDefinition definition : file.parameters()) {
            dictionary.addParameter(toEntity(definition));
        }
        for (CommandDefinitionYaml definition : file.commandsOrEmpty()) {
            dictionary.addCommand(toCommandEntity(definition));
        }

        if (activate) {
            // Clearing the previous active flag and setting the new one happen
            // in the same transaction as the insert. The partial unique index
            // on (active) would otherwise reject the second active row, which
            // is exactly the safety net we want if this ordering is ever broken.
            dictionaryRepository.findByActiveIsTrue()
                    .ifPresent(previous -> previous.setActive(false));
            dictionaryRepository.flush();
            dictionary.setActive(true);
        }

        TelemetryDictionary saved = dictionaryRepository.save(dictionary);
        log.info("Loaded dictionary version={} parameters={} commands={} active={}",
                saved.getVersion(), saved.getParameters().size(),
                saved.getCommands().size(), saved.isActive());
        return saved;
    }

    private TelemetryParameter toEntity(ParameterDefinition definition) {
        TelemetryParameter parameter = new TelemetryParameter(
                definition.mnemonic(),
                definition.name(),
                definition.apid(),
                definition.dataType(),
                definition.bitOffset(),
                definition.bitLength());

        parameter.setDescription(definition.description());
        parameter.setUnits(definition.units());
        parameter.setMinValue(definition.minValue());
        parameter.setMaxValue(definition.maxValue());
        parameter.setWarnLow(definition.warnLow());
        parameter.setWarnHigh(definition.warnHigh());
        parameter.setCalScale(definition.calScaleOrDefault());
        parameter.setCalOffset(definition.calOffsetOrDefault());

        for (EnumStateDefinition state : definition.enumStatesOrEmpty()) {
            parameter.addEnumState(new ParameterEnumState(state.rawValue(), state.label()));
        }
        return parameter;
    }

    private CommandDefinition toCommandEntity(CommandDefinitionYaml definition) {
        CommandDefinition command = new CommandDefinition(
                definition.mnemonic(), definition.name(),
                definition.apid(), definition.functionCode());
        command.setDescription(definition.description());
        command.setHazardous(definition.hazardousOrDefault());

        // Position comes from declaration order rather than being written out
        // in the file: arguments are packed into the uplink in the order they
        // are listed, so the file's order is already the source of truth and
        // restating it invites the two disagreeing.
        int position = 0;
        for (CommandDefinitionYaml.ArgumentYaml argumentYaml : definition.argumentsOrEmpty()) {
            CommandArgument argument = new CommandArgument(
                    argumentYaml.name(), argumentYaml.dataType(), position++);
            argument.setDescription(argumentYaml.description());
            argument.setMinValue(argumentYaml.minValue());
            argument.setMaxValue(argumentYaml.maxValue());
            argument.setAllowedValues(argumentYaml.allowedValuesCsv());
            argument.setRequired(argumentYaml.requiredOrDefault());
            command.addArgument(argument);
        }
        return command;
    }
}
