package com.solubris;

import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTList;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.counting;

public class ExcessiveMockingOnSameObjectRule extends AbstractJavaRule {
    private static final PropertyDescriptor<Integer> MAX_ALLOWED_DESCRIPTOR =
            PropertyFactory.intProperty("maxAllowed")
                    .desc("Maximum number of mockings allowed on the same object")
                    .defaultValue(3)
                    .build();

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public ExcessiveMockingOnSameObjectRule() {
        definePropertyDescriptor(MAX_ALLOWED_DESCRIPTOR);
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        int maxAllowed = getProperty(MAX_ALLOWED_DESCRIPTOR);

        Map<String, Long> mockCounts = node.descendants(ASTMethodCall.class)
                .filter(call -> Objects.equals("when", call.getMethodName()))
                .map(ASTMethodCall::getArguments)
                .filter(not(ASTList::isEmpty))
                .map(args -> args.get(0))
                .toStream()
                .map(ExcessiveMockingOnSameObjectRule::extractRootVariableName)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(identity(), counting()));

        RuleContext ruleContext = asCtx(data);
        mockCounts.entrySet().stream()
                .filter(e -> e.getValue() > maxAllowed)
                .forEach(entry -> ruleContext.addViolation(
                        node,
                        entry.getKey(),           // mock object name
                        entry.getValue()          // actual count
                ));

        return null;
    }

    /**
     * Attempts to extract the root variable name from an expression.
     * For example: returns "mockService" from expressions like:
     * - mockService
     * - mockService.doSomething()
     * - this.mockService
     * - another.mockService
     * Returns null if it cannot determine a simple variable name.
     */
    private static String extractRootVariableName(ASTExpression expr) {
        // Start from the **very first child** of the expression node
        // (in many cases this is already the core access node)
        JavaNode primary = expr.getFirstChild();

        // Keep going deeper into the **leftmost/first child chain** until we run out
        while (primary != null) {
            // If we hit a variable/field access node → that's usually what we want
            if (primary instanceof ASTVariableAccess astVariableAccess) {
                // Return the simple name of the variable/field (e.g. "mockList", "service")
                return astVariableAccess.getName();
            }

            // Otherwise keep descending left/down
            primary = primary.getFirstChild();
        }

        // Couldn't find any ASTVariableAccess → give up
        return null;
    }
}