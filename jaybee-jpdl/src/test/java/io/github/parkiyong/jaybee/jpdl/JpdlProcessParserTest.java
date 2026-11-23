package io.github.parkiyong.jaybee.jpdl;

import io.github.parkiyong.jaybee.api.JaybeeEngine;
import io.github.parkiyong.jaybee.api.exception.JpdlParseException;
import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.ActivityType;
import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.EndScope;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.ProcessDefinition;
import io.github.parkiyong.jaybee.api.spi.ParsedProcessDefinition;
import io.github.parkiyong.jaybee.core.JaybeeEngineBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JpdlProcessParserTest {

    private final JpdlProcessParser parser = new JpdlProcessParser();

    @Test
    void parsesAllPhase1ControlFlowElements() {
        String xml = """
                <process name="Order Flow" key="orderFlow" version="2" xmlns="http://jbpm.org/4.4/jpdl">
                  <description>Core order routing</description>
                  <start name="begin" g="10,20,48,48">
                    <transition to="checkAmount"/>
                  </start>
                  <decision name="checkAmount" g="80,20,48,48">
                    <transition name="high" to="split">
                      <condition expr="#{amount >= 100}"/>
                    </transition>
                    <transition name="low" to="waitSmall"/>
                  </decision>
                  <fork name="split" g="150,20,48,48">
                    <transition name="billing" to="billState"/>
                    <transition name="shipping" to="shipState"/>
                  </fork>
                  <state name="billState" g="220,10,90,50">
                    <transition to="merge"/>
                  </state>
                  <state name="shipState" g="220,80,90,50">
                    <transition to="merge"/>
                  </state>
                  <join name="merge" multiplicity="2" g="340,40,48,48">
                    <transition to="done"/>
                  </join>
                  <state name="waitSmall" g="150,120,90,50">
                    <transition name="cancel" to="cancelled"/>
                    <transition name="fail" to="failed"/>
                  </state>
                  <end name="done" g="420,40,48,48"/>
                  <end-cancel name="cancelled" ends="execution" g="300,120,48,48"/>
                  <end-error name="failed" g="300,180,48,48"/>
                </process>
                """;

        ParsedProcessDefinition def = parser.parse("order.jpdl.xml", xml.getBytes(StandardCharsets.UTF_8));
        assertEquals("orderFlow", def.key());
        assertEquals("Order Flow", def.name());
        assertEquals(2, def.explicitVersion());
        assertEquals("Core order routing", def.description());
        assertEquals(10, def.activities().size());

        ActivityDefinition start = def.activities().get(0);
        assertEquals(ActivityType.START, start.getType());
        assertEquals(new Coordinates(10, 20, 48, 48), start.getCoordinates());

        ActivityDefinition join = def.activities().stream()
                .filter(a -> a.getName().equals("merge")).findFirst().orElseThrow();
        assertEquals("2", join.getJoinMultiplicityExpression());

        ActivityDefinition cancelled = def.activities().stream()
                .filter(a -> a.getName().equals("cancelled")).findFirst().orElseThrow();
        assertEquals(EndScope.EXECUTION, cancelled.getEndScope());
        assertEquals(ExecutionStatus.CANCELLED, cancelled.getEndStatus());
        assertNull(cancelled.getEndOutcome());
    }

    @Test
    void parsesDecisionHandlerFieldsAndProperties() {
        String xml = """
                <process name="HandlerTest">
                  <start>
                    <transition to="route"/>
                  </start>
                  <decision name="route">
                    <handler class="com.example.MyHandler">
                      <field name="threshold"><int value="50"/></field>
                      <property name="tag"><string value="vip"/></property>
                      <field name="enabled"><true/></field>
                    </handler>
                    <transition name="ok" to="done"/>
                  </decision>
                  <end name="done"/>
                </process>
                """;

        ParsedProcessDefinition def = parser.parse("handler.jpdl.xml", xml.getBytes(StandardCharsets.UTF_8));
        ActivityDefinition decision = def.activities().stream()
                .filter(a -> a.getName().equals("route")).findFirst().orElseThrow();
        assertNotNull(decision.getDecisionHandler());
        assertEquals("com.example.MyHandler", decision.getDecisionHandler().className());
        assertEquals(3, decision.getDecisionHandler().fields().size());
        assertEquals(50, decision.getDecisionHandler().fields().get(0).value());
        assertFalse(decision.getDecisionHandler().fields().get(0).property());
        assertEquals("vip", decision.getDecisionHandler().fields().get(1).value());
        assertTrue(decision.getDecisionHandler().fields().get(1).property());
        assertEquals(Boolean.TRUE, decision.getDecisionHandler().fields().get(2).value());
    }

    @Test
    void failsFastOnUnsupportedPhase2Elements() {
        String taskXml = """
                <process name="HasTask">
                  <start><transition to="review"/></start>
                  <task name="review" assignee="alex"><transition to="end"/></task>
                  <end name="end"/>
                </process>
                """;
        JpdlParseException ex = assertThrows(
                JpdlParseException.class,
                () -> parser.parse("task.jpdl.xml", taskXml.getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(ex.getMessage().contains("<task>"));

        String timerXml = """
                <process name="HasTimer">
                  <start><transition to="wait"/></start>
                  <state name="wait">
                    <transition to="end">
                      <timer duedate="10 minutes"/>
                    </transition>
                  </state>
                  <end name="end"/>
                </process>
                """;
        assertThrows(
                JpdlParseException.class,
                () -> parser.parse("timer.jpdl.xml", timerXml.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void rejectsUnknownTransitionTargetDuplicateNamesAndDoctype() {
        String badTarget = """
                <process name="BadTarget">
                  <start><transition to="missing"/></start>
                  <end name="end"/>
                </process>
                """;
        assertThrows(JpdlParseException.class,
                () -> parser.parse("bad.jpdl.xml", badTarget.getBytes(StandardCharsets.UTF_8)));

        String dupNames = """
                <process name="Dup">
                  <start name="a"><transition to="end"/></start>
                  <state name="a"><transition to="end"/></state>
                  <end name="end"/>
                </process>
                """;
        assertThrows(JpdlParseException.class,
                () -> parser.parse("dup.jpdl.xml", dupNames.getBytes(StandardCharsets.UTF_8)));

        String xxe = """
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <process name="XXE"><start><transition to="e"/></start><end name="e"/></process>
                """;
        assertThrows(JpdlParseException.class,
                () -> parser.parse("xxe.jpdl.xml", xxe.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deploysBarZipArchiveAndExposesResources() throws Exception {
        String xml = """
                <process name="ZipProcess">
                  <start><transition to="s1"/></start>
                  <state name="s1" g="5,6,7,8"><transition to="done"/></state>
                  <end name="done"/>
                </process>
                """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("zip.jpdl.xml"));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("notes.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        try (JaybeeEngine engine = JaybeeEngineBuilder.create().registerParser(new JpdlProcessParser()).build()) {
            String depId = engine.repository().newDeployment()
                    .named("bar-archive")
                    .addZipArchive(new ZipInputStream(new ByteArrayInputStream(baos.toByteArray())))
                    .deploy();

            assertEquals(Set.of("zip.jpdl.xml", "notes.txt"), engine.repository().listResourceNames(depId));
            try (InputStream notes = engine.repository().openResource(depId, "notes.txt").orElseThrow()) {
                assertEquals("hello", new String(notes.readAllBytes(), StandardCharsets.UTF_8));
            }
            ProcessDefinition pd = engine.repository().definitions()
                    .inDeployment(depId).fetchOne().orElseThrow();
            assertEquals("ZipProcess-1", pd.getId());
            assertEquals(new Coordinates(5, 6, 7, 8),
                    engine.repository().findActivityCoordinates(pd.getId(), "s1").orElseThrow());
        }
    }
}
