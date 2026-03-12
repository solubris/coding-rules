package com.solubris.pmd;

import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.reporting.RuleContext;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

public class UnmodifiableSetCollectorRule extends AbstractJavaRule {
    @Override
    public Object visit(ASTMethodCall node, Object data) {
        Optional.of(node)
                .filter(n -> n.getMethodName().equals("collect"))
                .map(ASTMethodCall::getArguments)
                .filter(args -> args.size() == 1)
                .map(args -> args.getFirstChild())
                .map(UnmodifiableSetCollectorRule::unwrapMethodCall)
                .filter(argCall -> argCall.getMethodName().equals("toSet"))
                .filter(UnmodifiableSetCollectorRule::isCollectorsMethod)
                .ifPresent(methodCall -> addViolation(node, asCtx(data), methodCall.getQualifier()));

        return data;
    }

    /**
     * The toSet() may or may not be qualified by Collectors class.
     */
    private static boolean isCollectorsMethod(ASTMethodCall methodCall) {
        ASTExpression qualifier = methodCall.getQualifier();
        if (qualifier == null) return true;
        return "Collectors".equals(qualifier.getFirstToken().getImage());
    }

    private static void addViolation(ASTMethodCall node, RuleContext ctx, @Nullable ASTExpression qualifier) {
        String qualifierPrefix = qualifier == null ? "" : qualifier.getFirstToken().getImage() + ".";
        ctx.addViolation(node,
                ".collect(" + qualifierPrefix + "toSet()) should be replaced with .collect(" + qualifierPrefix + "toUnmodifiableSet())");
    }

    // Recursively unwraps ASTExpression nodes to find an ASTMethodCall
    private static ASTMethodCall unwrapMethodCall(Object node) {
        if (node instanceof ASTMethodCall astMethodCall) return astMethodCall;

        if (node instanceof ASTExpression expr && expr.getNumChildren() == 1) {
            return unwrapMethodCall(expr.getChild(0));
        }

        return null;
    }
}