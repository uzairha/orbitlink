package com.orbitlink.server.dictionary;

import com.orbitlink.server.dictionary.validation.DictionaryValidator;
import com.orbitlink.server.dictionary.validation.ValidationReport;
import com.orbitlink.server.dictionary.yaml.DictionaryFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST surface for inspecting, validating and loading telemetry dictionaries. */
@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final DictionaryLoader loader;
    private final DictionaryValidator validator;
    private final TelemetryDictionaryRepository repository;

    public DictionaryController(DictionaryLoader loader,
                                DictionaryValidator validator,
                                TelemetryDictionaryRepository repository) {
        this.loader = loader;
        this.validator = validator;
        this.repository = repository;
    }

    @GetMapping
    public List<DictionarySummaryView> list() {
        return repository.findAllSummaries();
    }

    /**
     * Dry-runs validation against a candidate YAML file and returns the report
     * without persisting anything.
     *
     * <p>Always answers 200 when the file parses, even for an invalid
     * dictionary — the report <em>is</em> the successful result of asking "what
     * is wrong with this file". A 4xx is reserved for a request that could not
     * be processed at all, which here means YAML that will not parse.
     */
    @PostMapping(path = "/validate", consumes = {"text/plain", "application/yaml", "application/x-yaml"})
    public ValidationReport validate(@RequestBody String yaml) throws IOException {
        DictionaryFile file = loader.parse(toStream(yaml));
        return validator.validate(file);
    }

    /**
     * Loads a dictionary. Rejected with 422 and the full report if it has any
     * ERROR findings.
     */
    @PostMapping(consumes = {"text/plain", "application/yaml", "application/x-yaml"})
    public ResponseEntity<DictionarySummaryView> load(
            @RequestBody String yaml,
            @RequestParam(defaultValue = "false") boolean activate) throws IOException {

        DictionaryFile file = loader.parse(toStream(yaml));
        TelemetryDictionary saved = loader.load(file, "api-upload", activate);

        // Safe to read the collection here: it was populated in memory by the
        // loader on the way in, so this touches no lazy proxy.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DictionarySummaryView(
                        saved.getVersion(), saved.getDescription(),
                        saved.isActive(), saved.getParameters().size()));
    }

    /**
     * 422 Unprocessable Entity, not 400: the request was well-formed and
     * understood, but its contents are semantically wrong. The body is the
     * validation report so the caller gets every problem in one response.
     */
    @ExceptionHandler(InvalidDictionaryException.class)
    public ResponseEntity<ValidationReport> handleInvalidDictionary(InvalidDictionaryException e) {
        return ResponseEntity.unprocessableEntity().body(e.getReport());
    }

    /** A YAML file that will not parse, or one missing version/parameters. */
    @ExceptionHandler({IOException.class, IllegalArgumentException.class})
    public ResponseEntity<String> handleUnparseable(Exception e) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    /** Re-loading an existing version. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    private static ByteArrayInputStream toStream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
