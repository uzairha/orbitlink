package com.orbitlink.server.command;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandDefinitionRepository extends JpaRepository<CommandDefinition, Long> {

    /**
     * Looks the command up in the ACTIVE dictionary only. A command defined in
     * a superseded dictionary version must not be sendable — the spacecraft no
     * longer accepts it.
     *
     * <p>The entity graph pulls the arguments in the same query, since
     * validation needs every one of them immediately.
     */
    @EntityGraph(attributePaths = "arguments")
    Optional<CommandDefinition> findByMnemonicAndDictionaryActiveIsTrue(String mnemonic);

    @EntityGraph(attributePaths = "arguments")
    List<CommandDefinition> findByDictionaryActiveIsTrueOrderByMnemonic();
}
