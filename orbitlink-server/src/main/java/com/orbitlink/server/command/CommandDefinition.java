package com.orbitlink.server.command;

import com.orbitlink.server.dictionary.TelemetryDictionary;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** One command the spacecraft accepts, and the arguments it takes. */
@Entity
@Table(name = "command_definition")
public class CommandDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dictionary_id", nullable = false)
    private TelemetryDictionary dictionary;

    @Column(nullable = false, length = 64)
    private String mnemonic;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int apid;

    @Column(name = "function_code", nullable = false)
    private int functionCode;

    @Column(nullable = false)
    private boolean hazardous;

    /** Ordered by position: arguments are packed into the uplink in sequence. */
    @OneToMany(mappedBy = "command", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<CommandArgument> arguments = new ArrayList<>();

    protected CommandDefinition() {
        // Required by JPA.
    }

    public CommandDefinition(String mnemonic, String name, int apid, int functionCode) {
        this.mnemonic = mnemonic;
        this.name = name;
        this.apid = apid;
        this.functionCode = functionCode;
    }

    public void addArgument(CommandArgument argument) {
        arguments.add(argument);
        argument.setCommand(this);
    }

    public Long getId() {
        return id;
    }

    public TelemetryDictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(TelemetryDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getApid() {
        return apid;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public boolean isHazardous() {
        return hazardous;
    }

    public void setHazardous(boolean hazardous) {
        this.hazardous = hazardous;
    }

    public List<CommandArgument> getArguments() {
        return arguments;
    }
}
