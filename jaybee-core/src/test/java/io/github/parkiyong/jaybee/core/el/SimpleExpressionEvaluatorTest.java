package io.github.parkiyong.jaybee.core.el;

import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleExpressionEvaluatorTest {

    private final SimpleExpressionEvaluator evaluator = new SimpleExpressionEvaluator();

    public record Customer(String name, String tier, boolean active) {
    }

    public static class Order {
        private final int amount;
        private final Customer customer;

        public Order(int amount, Customer customer) {
            this.amount = amount;
            this.customer = customer;
        }

        public int getAmount() {
            return amount;
        }

        public Customer getCustomer() {
            return customer;
        }
    }

    @Test
    void evaluatesArithmeticAndPrecedence() {
        Map<String, Object> vars = Map.of("a", 10, "b", 3);
        assertEquals(16L, evaluator.evaluate("#{a + b * 2}", vars::get));
        assertEquals(26L, evaluator.evaluate("#{(a + b) * 2}", vars::get));
        assertEquals(1L, evaluator.evaluate("#{a mod b}", vars::get));
        assertEquals(-10L, evaluator.evaluate("#{-a}", vars::get));
        assertEquals(2.5, evaluator.evaluate("#{5 / 2}", vars::get));
    }

    @Test
    void evaluatesComparisonsLogicalAndTernary() {
        Map<String, Object> vars = Map.of(
                "content", "good",
                "amount", 250,
                "vip", false
        );

        assertEquals("to a", evaluator.evaluate("#{content == 'good' ? 'to a' : 'to b'}", vars::get));
        assertTrue(evaluator.evaluateBoolean("#{amount >= 200 and (vip or content eq 'good')}", vars::get));
        assertTrue(evaluator.evaluateBoolean("#{not vip && amount != 100}", vars::get));
        assertFalse(evaluator.evaluateBoolean("#{amount lt 100 || vip}", vars::get));
    }

    @Test
    void evaluatesNestedPropertiesMapsListsAndRecords() {
        Order order = new Order(500, new Customer("Alice", "GOLD", true));
        Map<String, Object> vars = Map.of(
                "order", order,
                "meta", Map.of("region", "KR"),
                "items", List.of("book", "pen")
        );

        assertEquals("GOLD", evaluator.evaluate("#{order.customer.tier}", vars::get));
        assertTrue(evaluator.evaluateBoolean("#{order.amount > 100 && order.customer.active}", vars::get));
        assertEquals("KR", evaluator.evaluate("#{meta['region']}", vars::get));
        assertEquals("pen", evaluator.evaluate("#{items[1]}", vars::get));
        assertNull(evaluator.evaluate("#{missing.nested.prop}", vars::get));
    }

    @Test
    void evaluatesEmptyAndTemplates() {
        Map<String, Object> vars = Map.of(
                "emptyStr", "",
                "emptyList", List.of(),
                "nonEmptyList", List.of(1),
                "name", "Jaybee"
        );

        assertTrue(evaluator.evaluateBoolean("#{empty missingVar}", vars::get));
        assertTrue(evaluator.evaluateBoolean("#{empty emptyStr}", vars::get));
        assertTrue(evaluator.evaluateBoolean("#{empty emptyList}", vars::get));
        assertFalse(evaluator.evaluateBoolean("#{empty nonEmptyList}", vars::get));
        assertEquals("Hello, Jaybee!", evaluator.evaluate("Hello, #{name}!", vars::get));
    }

    @Test
    void rejectsMalformedExpressions() {
        assertThrows(JaybeeException.class, () -> evaluator.evaluate("#{1 + }", k -> null));
        assertThrows(JaybeeException.class, () -> evaluator.evaluate("#{'unterminated}", k -> null));
    }
}
