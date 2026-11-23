package io.github.parkiyong.jaybee.jpdl;

import io.github.parkiyong.jaybee.api.exception.JpdlParseException;
import io.github.parkiyong.jaybee.api.model.ActivityDefinition;
import io.github.parkiyong.jaybee.api.model.ActivityType;
import io.github.parkiyong.jaybee.api.model.Coordinates;
import io.github.parkiyong.jaybee.api.model.EndScope;
import io.github.parkiyong.jaybee.api.model.ExecutionStatus;
import io.github.parkiyong.jaybee.api.model.HandlerDescriptor;
import io.github.parkiyong.jaybee.api.model.HandlerField;
import io.github.parkiyong.jaybee.api.model.TransitionDefinition;
import io.github.parkiyong.jaybee.api.spi.ParsedProcessDefinition;
import io.github.parkiyong.jaybee.api.spi.ProcessDefinitionParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Clean-room strict fail-fast jPDL 4.4 XML parser for Phase 1 control-flow elements
 * ({@code start}, {@code state}, {@code decision}, {@code fork}, {@code join},
 * {@code end}, {@code end-cancel}, {@code end-error}).
 */
public final class JpdlProcessParser implements ProcessDefinitionParser {

    @Override
    public boolean supports(String resourceName) {
        return resourceName != null && resourceName.toLowerCase(Locale.ROOT).endsWith(".jpdl.xml");
    }

    @Override
    public ParsedProcessDefinition parse(String resourceName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new JpdlParseException("Empty jPDL resource: " + resourceName);
        }
        Document document = parseXml(resourceName, content);
        Element root = document.getDocumentElement();
        if (root == null || !"process".equals(localName(root))) {
            throw new JpdlParseException(
                    "Expected root <process> element in '" + resourceName + "', found <"
                            + (root == null ? "null" : localName(root)) + ">"
            );
        }

        String name = requiredAttr(root, "name", resourceName);
        String keyAttr = optionalAttr(root, "key");
        String key = (keyAttr != null && !keyAttr.isBlank()) ? keyAttr.trim() : deriveKey(name);
        Integer explicitVersion = parseOptionalInt(optionalAttr(root, "version"), "version", resourceName);
        String description = optionalAttr(root, "description");

        List<ActivityDefinition> activities = new ArrayList<>();
        Set<String> activityNames = new LinkedHashSet<>();

        for (Element child : childElements(root)) {
            String tag = localName(child);
            if ("description".equals(tag)) {
                String text = child.getTextContent();
                if (text != null && !text.isBlank()) {
                    description = text.trim();
                }
                continue;
            }
            ActivityDefinition activity = parseActivity(child, tag, resourceName);
            if (!activityNames.add(activity.getName())) {
                throw new JpdlParseException(
                        "Duplicate activity name '" + activity.getName() + "' in '" + resourceName + "'"
                );
            }
            activities.add(activity);
        }

        validateProcessGraph(resourceName, activities, activityNames);

        return new ParsedProcessDefinition(
                key,
                name,
                explicitVersion,
                description,
                null,
                activities
        );
    }

    private ActivityDefinition parseActivity(Element element, String tag, String resourceName) {
        return switch (tag) {
            case "start" -> parseStart(element, resourceName);
            case "state" -> parseSimpleNode(element, ActivityType.STATE, resourceName);
            case "decision" -> parseDecision(element, resourceName);
            case "fork" -> parseSimpleNode(element, ActivityType.FORK, resourceName);
            case "join" -> parseJoin(element, resourceName);
            case "end" -> parseEnd(element, ActivityType.END, ExecutionStatus.COMPLETED, resourceName);
            case "end-cancel" -> parseEnd(element, ActivityType.END_CANCEL, ExecutionStatus.CANCELLED, resourceName);
            case "end-error" -> parseEnd(element, ActivityType.END_ERROR, ExecutionStatus.FAILED, resourceName);
            default -> throw new JpdlParseException(
                    "Unsupported jPDL element <" + tag + "> in '" + resourceName
                            + "' (not enabled in Phase 1 core control-flow scope)"
            );
        };
    }

    private ActivityDefinition parseStart(Element element, String resourceName) {
        String name = optionalAttr(element, "name");
        if (name == null || name.isBlank()) {
            name = "start";
        }
        Coordinates coords = parseCoordinates(optionalAttr(element, "g"), resourceName);
        List<TransitionDefinition> transitions = parseTransitions(element, name, false, resourceName);
        return ActivityDefinition.builder(name, ActivityType.START)
                .coordinates(coords)
                .outgoingTransitions(transitions)
                .build();
    }

    private ActivityDefinition parseSimpleNode(Element element, ActivityType type, String resourceName) {
        String name = requiredAttr(element, "name", resourceName);
        Coordinates coords = parseCoordinates(optionalAttr(element, "g"), resourceName);
        List<TransitionDefinition> transitions = parseTransitions(element, name, false, resourceName);
        return ActivityDefinition.builder(name, type)
                .coordinates(coords)
                .outgoingTransitions(transitions)
                .build();
    }

    private ActivityDefinition parseJoin(Element element, String resourceName) {
        String name = requiredAttr(element, "name", resourceName);
        Coordinates coords = parseCoordinates(optionalAttr(element, "g"), resourceName);
        String multiplicity = optionalAttr(element, "multiplicity");
        List<TransitionDefinition> transitions = parseTransitions(element, name, false, resourceName);
        return ActivityDefinition.builder(name, ActivityType.JOIN)
                .coordinates(coords)
                .joinMultiplicityExpression(multiplicity)
                .outgoingTransitions(transitions)
                .build();
    }

    private ActivityDefinition parseDecision(Element element, String resourceName) {
        String name = requiredAttr(element, "name", resourceName);
        Coordinates coords = parseCoordinates(optionalAttr(element, "g"), resourceName);
        String expr = optionalAttr(element, "expr");

        HandlerDescriptor handler = null;
        List<TransitionDefinition> transitions = new ArrayList<>();

        for (Element child : childElements(element)) {
            String tag = localName(child);
            switch (tag) {
                case "transition" -> transitions.add(parseTransition(child, name, true, resourceName));
                case "handler" -> {
                    if (handler != null) {
                        throw new JpdlParseException(
                                "Multiple <handler> elements declared in <decision name=\"" + name + "\"> in '"
                                        + resourceName + "'"
                        );
                    }
                    handler = parseHandler(child, resourceName);
                }
                case "description" -> {
                    // optional metadata
                }
                default -> throw new JpdlParseException(
                        "Unsupported child element <" + tag + "> in <decision name=\"" + name + "\"> in '"
                                + resourceName + "'"
                );
            }
        }

        return ActivityDefinition.builder(name, ActivityType.DECISION)
                .coordinates(coords)
                .decisionExpression(expr)
                .decisionHandler(handler)
                .outgoingTransitions(transitions)
                .build();
    }

    private ActivityDefinition parseEnd(
            Element element,
            ActivityType type,
            ExecutionStatus defaultStatus,
            String resourceName
    ) {
        String name = requiredAttr(element, "name", resourceName);
        Coordinates coords = parseCoordinates(optionalAttr(element, "g"), resourceName);
        String stateAttr = optionalAttr(element, "state");
        String customOutcome = (stateAttr != null && !stateAttr.isBlank()) ? stateAttr.trim() : null;

        String endsAttr = optionalAttr(element, "ends");
        EndScope endScope = EndScope.PROCESS_INSTANCE;
        if (endsAttr != null && !endsAttr.isBlank()) {
            endScope = switch (endsAttr.trim()) {
                case "process-instance" -> EndScope.PROCESS_INSTANCE;
                case "execution" -> EndScope.EXECUTION;
                default -> throw new JpdlParseException(
                        "Invalid ends attribute '" + endsAttr + "' on <" + type.xmlTag()
                                + " name=\"" + name + "\"> in '" + resourceName + "'"
                );
            };
        }

        for (Element child : childElements(element)) {
            String tag = localName(child);
            if (!"description".equals(tag)) {
                throw new JpdlParseException(
                        "Unsupported child element <" + tag + "> in <" + type.xmlTag()
                                + " name=\"" + name + "\"> in '" + resourceName + "'"
                );
            }
        }

        return ActivityDefinition.builder(name, type)
                .coordinates(coords)
                .endStatus(defaultStatus)
                .endOutcome(customOutcome)
                .endScope(endScope)
                .build();
    }

    private List<TransitionDefinition> parseTransitions(
            Element activityElement,
            String activityName,
            boolean allowCondition,
            String resourceName
    ) {
        List<TransitionDefinition> transitions = new ArrayList<>();
        for (Element child : childElements(activityElement)) {
            String tag = localName(child);
            if ("transition".equals(tag)) {
                transitions.add(parseTransition(child, activityName, allowCondition, resourceName));
            } else if ("description".equals(tag)) {
                // optional metadata
            } else {
                throw new JpdlParseException(
                        "Unsupported child element <" + tag + "> inside <" + localName(activityElement)
                                + " name=\"" + activityName + "\"> in '" + resourceName + "'"
                );
            }
        }
        return transitions;
    }

    private TransitionDefinition parseTransition(
            Element element,
            String sourceName,
            boolean allowCondition,
            String resourceName
    ) {
        String name = optionalAttr(element, "name");
        String to = requiredAttr(element, "to", resourceName);
        String conditionExpr = null;

        for (Element child : childElements(element)) {
            String tag = localName(child);
            if ("condition".equals(tag)) {
                if (!allowCondition) {
                    throw new JpdlParseException(
                            "<condition> is only supported on <decision> transitions in '" + resourceName + "'"
                    );
                }
                conditionExpr = requiredAttr(child, "expr", resourceName);
            } else if ("description".equals(tag)) {
                // optional metadata
            } else {
                throw new JpdlParseException(
                        "Unsupported child element <" + tag + "> inside <transition> from '"
                                + sourceName + "' in '" + resourceName + "'"
                );
            }
        }

        return new TransitionDefinition(name, sourceName, to, conditionExpr);
    }

    private HandlerDescriptor parseHandler(Element element, String resourceName) {
        String className = optionalAttr(element, "class");
        String expr = optionalAttr(element, "expr");
        if ((className == null || className.isBlank()) && (expr == null || expr.isBlank())) {
            throw new JpdlParseException(
                    "<handler> must specify either 'class' or 'expr' attribute in '" + resourceName + "'"
            );
        }

        List<HandlerField> fields = new ArrayList<>();
        for (Element child : childElements(element)) {
            String tag = localName(child);
            if ("field".equals(tag) || "property".equals(tag)) {
                String fieldName = requiredAttr(child, "name", resourceName);
                Object value = parseInjectionValue(child, resourceName);
                fields.add(new HandlerField(fieldName, value, "property".equals(tag)));
            } else {
                throw new JpdlParseException(
                        "Unsupported child element <" + tag + "> inside <handler> in '" + resourceName + "'"
                );
            }
        }
        return new HandlerDescriptor(className, expr, fields);
    }

    private Object parseInjectionValue(Element fieldElement, String resourceName) {
        List<Element> children = childElements(fieldElement);
        if (children.isEmpty()) {
            String attrVal = optionalAttr(fieldElement, "value");
            if (attrVal != null) {
                return attrVal;
            }
            return fieldElement.getTextContent();
        }
        if (children.size() > 1) {
            throw new JpdlParseException(
                    "Expected a single value element inside <" + localName(fieldElement) + "> in '"
                            + resourceName + "'"
            );
        }
        Element valueEl = children.get(0);
        String tag = localName(valueEl);
        return switch (tag) {
            case "string" -> optionalAttr(valueEl, "value") != null
                    ? optionalAttr(valueEl, "value")
                    : valueEl.getTextContent();
            case "int" -> Integer.parseInt(requiredAttr(valueEl, "value", resourceName).trim());
            case "long" -> Long.parseLong(requiredAttr(valueEl, "value", resourceName).trim());
            case "double" -> Double.parseDouble(requiredAttr(valueEl, "value", resourceName).trim());
            case "float" -> Float.parseFloat(requiredAttr(valueEl, "value", resourceName).trim());
            case "short" -> Short.parseShort(requiredAttr(valueEl, "value", resourceName).trim());
            case "byte" -> Byte.parseByte(requiredAttr(valueEl, "value", resourceName).trim());
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            case "null" -> null;
            default -> throw new JpdlParseException(
                    "Unsupported injection value element <" + tag + "> in '" + resourceName + "'"
            );
        };
    }

    private void validateProcessGraph(
            String resourceName,
            List<ActivityDefinition> activities,
            Set<String> activityNames
    ) {
        boolean hasStart = false;
        for (ActivityDefinition activity : activities) {
            if (activity.getType() == ActivityType.START) {
                hasStart = true;
            }
            for (TransitionDefinition transition : activity.getOutgoingTransitions()) {
                if (!activityNames.contains(transition.destinationName())) {
                    throw new JpdlParseException(
                            "Transition from '" + activity.getName() + "' targets unknown activity '"
                                    + transition.destinationName() + "' in '" + resourceName + "'"
                    );
                }
            }
        }
        if (!hasStart) {
            throw new JpdlParseException("Process in '" + resourceName + "' must declare at least one <start> activity");
        }
    }

    private Document parseXml(String resourceName, byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new JpdlParseException("Failed to parse jPDL XML '" + resourceName + "': " + e.getMessage(), e);
        }
    }

    private Coordinates parseCoordinates(String gAttr, String resourceName) {
        if (gAttr == null || gAttr.isBlank()) {
            return null;
        }
        String[] parts = gAttr.split(",");
        if (parts.length != 4) {
            throw new JpdlParseException(
                    "Invalid coordinates attribute g=\"" + gAttr + "\" in '" + resourceName + "'"
            );
        }
        try {
            return new Coordinates(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim())
            );
        } catch (NumberFormatException e) {
            throw new JpdlParseException(
                    "Invalid integer in coordinates attribute g=\"" + gAttr + "\" in '" + resourceName + "'", e
            );
        }
    }

    private Integer parseOptionalInt(String raw, String attrName, String resourceName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new JpdlParseException(
                    "Invalid integer for attribute '" + attrName + "'='" + raw + "' in '" + resourceName + "'", e
            );
        }
    }

    private String deriveKey(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private String localName(Element element) {
        String local = element.getLocalName();
        return local != null ? local : element.getTagName();
    }

    private String optionalAttr(Element element, String attrName) {
        if (!element.hasAttribute(attrName)) {
            return null;
        }
        return element.getAttribute(attrName);
    }

    private String requiredAttr(Element element, String attrName, String resourceName) {
        String val = optionalAttr(element, attrName);
        if (val == null || val.isBlank()) {
            throw new JpdlParseException(
                    "Missing required attribute '" + attrName + "' on <" + localName(element)
                            + "> in '" + resourceName + "'"
            );
        }
        return val.trim();
    }

    private List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el) {
                result.add(el);
            }
        }
        return result;
    }
}
