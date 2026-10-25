// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde;

import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.StatementExpression;
import dsl.flows.sde.models.statements.VariableOrPort;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.TypeReference;

import java.util.Collections;
import java.util.TreeMap;

public class MacroBuilder {
    public final BitType bitType;
    public final Transform transform;
    public final SourceFilenameAndLocationRange locationRange;

    public MacroBuilder(BitType bitType, Transform transform, SourceFilenameAndLocationRange locationRange) {
        this.bitType = bitType;
        this.transform = transform;
        this.locationRange = locationRange;
    }

    public ExpressionFunction concatenate(Expression... positionalParameters) {
        return new ExpressionFunction(locationRange, "Concatenate", positionalParameters, Collections.EMPTY_MAP,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator sum(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.IntegerSum, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator difference(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.IntegerDifference, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionConstant constant(int value, int width) {
        return new ExpressionConstant(locationRange, value, width, transform.idGenerator.generateId());
    }

    public ExpressionName name(VariableOrPort variableOrPort) {
        return new ExpressionName(locationRange, variableOrPort, bitType, transform.idGenerator.generateId());
    }

    public ExpressionFunction slice(Expression whole, Expression offsetInBits, Expression countInBits) {
        TreeMap<String, FieldOrParameter> whatSliceParameters = new TreeMap<>();
        whatSliceParameters.put("whole", new FieldOrParameter(null, whole));
        whatSliceParameters.put("offsetInBits", new FieldOrParameter(null, offsetInBits));
        whatSliceParameters.put("countInBits", new FieldOrParameter(null, countInBits));
        return new ExpressionFunction(locationRange, "Slice", Expression.EMPTY_ARRAY, whatSliceParameters,
                transform.idGenerator.generateId());
    }

    public ExpressionFunction ifte(Expression condition, Expression thenValue, Expression elseValue) {
        TreeMap<String, FieldOrParameter> ifParameters = new TreeMap<>();
        ifParameters.put("condition", new FieldOrParameter(null, condition));
        ifParameters.put("thenValue", new FieldOrParameter(null, thenValue));
        ifParameters.put("elseValue", new FieldOrParameter(null, elseValue));
        return new ExpressionFunction(locationRange, "If", Expression.EMPTY_ARRAY, ifParameters,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator more(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.More, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator moreOrEqual(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.MoreOrEqual, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator less(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.Less, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator lessOrEqual(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.LessOrEqual, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator equal(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.Equals, a, b,
                transform.idGenerator.generateId());
    }

    public StatementExpression expression(VariableOrPort assignmentTarget, Expression expression,
                                          String syntheticVariableSuffix) {
        return new StatementExpression(locationRange, assignmentTarget, expression, syntheticVariableSuffix,
                transform.idGenerator.generateId());
    }

    public ExpressionPart part(Expression expression, String name) {
        return new ExpressionPart(locationRange, expression, name, transform.idGenerator.generateId());
    }

    public ExpressionBinaryOperator and(Expression a, Expression b) {
        return new ExpressionBinaryOperator(locationRange, ExpressionBinaryOperator.Kind.BooleanAnd, a, b,
                transform.idGenerator.generateId());
    }

    public ExpressionFunction min(TypeReference typeReference, Expression... positionalParameters) {
        ExpressionFunction result = new ExpressionFunction(locationRange, "Min", positionalParameters,
                Collections.EMPTY_MAP, transform.idGenerator.generateId());
        result.resolvedTypeReference = typeReference;
        return result;
    }

    public ExpressionFunction max(Expression... positionalParameters) {
        return new ExpressionFunction(locationRange, "Max", positionalParameters, Collections.EMPTY_MAP,
                transform.idGenerator.generateId());
    }

    public Expression is(Expression expression, TypeReference type) {
        return new ExpressionIs(locationRange, expression, type, transform.idGenerator.generateId());
    }
}
