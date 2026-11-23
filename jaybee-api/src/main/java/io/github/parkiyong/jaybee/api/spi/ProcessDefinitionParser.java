package io.github.parkiyong.jaybee.api.spi;

/**
 * SPI for parsing deployment resources (such as {@code *.jpdl.xml}) into a process definition AST.
 */
public interface ProcessDefinitionParser {

    boolean supports(String resourceName);

    ParsedProcessDefinition parse(String resourceName, byte[] content);
}
