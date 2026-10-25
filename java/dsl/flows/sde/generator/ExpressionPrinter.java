// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.generator;

import dsl.flows.sde.models.ConstantDeclaration;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.Statement;
import dsl.flows.sde.models.statements.StatementStep;
import dsl.flows.sde.models.statements.VariableOrPort;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.OneOfType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.TypeReference;

import java.io.PrintStream;
import java.util.TreeSet;
import java.util.function.BiFunction;

class ExpressionPrinter {
    final PrintStream module;
    final StringBuilder output = new StringBuilder();
    final StatementStep region;
    final Statement location;
    final TreeSet<String> emittedExpressions;

    ExpressionPrinter(PrintStream module, StatementStep region, Statement location,
                      TreeSet<String> emittedExpressions) {
        this.module = module;
        this.region = region;
        this.location = location;
        this.emittedExpressions = emittedExpressions;
    }

    void print(Expression expression) {
        if (expression instanceof ExpressionFunction cast) {
            switch (cast.name) {
                case "Concatenate" -> {
                    output.append("{");
                    String separator = "";
                    for (Expression parameter : cast.positionalParameters) {
                        output.append(separator);
                        print(parameter);
                        separator = ",\n";
                    }
                    output.append("}");
                    return;
                }
                case "If" -> {
                    output.append("(");
                    print(cast.namedParameters.get("condition").expression());
                    output.append(") ? (");
                    print(cast.namedParameters.get("thenValue").expression());
                    output.append(") : (");
                    print(cast.namedParameters.get("elseValue").expression());
                    output.append(")");
                    return;
                }
                case "Slice" -> {
                    Expression whole = cast.namedParameters.get("whole").expression();
                    if (whole instanceof ExpressionPart || whole instanceof ExpressionName) {
                        print(whole);
                    } else {
                        String variableName = "I" + region.suffixForVariables + "_" + whole.id;
                        if (emittedExpressions.add(variableName)) {
                            ExpressionPrinter sub = new ExpressionPrinter(module, region, location, emittedExpressions);
                            sub.print(whole);
                            module.println(whole.resolvedTypeReference.toSVString() + " " + variableName + ";");
                            module.println("assign " + variableName + " = " + sub.output + ";");
                        }
                        output.append(variableName);
                    }
                    output.append("[");
                    print(cast.namedParameters.get("offsetInBits").expression());
                    output.append("+:");
                    print(cast.namedParameters.get("countInBits").expression());
                    output.append("]");
                    return;
                }
                case "ShiftLeft" -> {
                    output.append("(");
                    print(cast.namedParameters.get("what").expression());
                    output.append(") << (");
                    print(cast.namedParameters.get("howManyBits").expression());
                    output.append(")");
                    return;
                }
                case "ShiftRight" -> {
                    output.append("(");
                    print(cast.namedParameters.get("what").expression());
                    output.append(") >> (");
                    print(cast.namedParameters.get("howManyBits").expression());
                    output.append(")");
                    return;
                }
                case "Min" -> {
                    printMinMax(cast, (a, b) -> a + "<" + b + "?" + a + ":" + b);
                    return;
                }
                case "Max" -> {
                    printMinMax(cast, (a, b) -> a + ">" + b + "?" + a + ":" + b);
                    return;
                }
                case "InternetChecksum" -> {
                    output.append("combineInternetChecksum(");
                    String prefix = "I" + region.suffixForVariables + "_" + location.suffixForVariables + "_" + cast.id;
                    String variableName = prefix + "_0_" + cast.positionalParameters.length;
                    if (emittedExpressions.add(variableName)) {
                        printCombineTree(new TypeReference(null, "Bit", new BitType(), 64), prefix, 0,
                                cast.positionalParameters.length, cast.positionalParameters,
                                (a, b) -> "combineInternetChecksum(" + a + "," + b + ")");
                    }
                    output.append(variableName).append(",0)");
                    return;
                }
            }
        }
        if (expression instanceof ExpressionName cast) {
            if (cast.resolvedEntity instanceof ConstantDeclaration castConstant) {
                output.append("64'h").append(Long.toHexString(castConstant.value));
            } else {
                VariableOrPort variableOrPort = (VariableOrPort) cast.resolvedEntity;
                output.append(Generator.getVersionTerminal(
                        location.regionAttributes.get(region).versionAtEntryByVariableOrPort.get(variableOrPort),
                        variableOrPort));
            }
            return;
        }
        if (expression instanceof ExpressionUnaryOperator cast) {
            output.append("~(");
            print(cast.sub);
            output.append(")");
            return;
        }
        if (expression instanceof ExpressionObject cast) {
            output.append(cast.resolvedTypeReference.name).append("'{");
            String separator = "";
            for (StructType.Field field : ((StructType) cast.resolvedTypeReference.resolvedType).fields) {
                output.append(separator).append(field.name()).append(": ");
                FieldOrParameter value = cast.fields.get(field.name());
                if (value != null) {
                    print(value.expression());
                } else {
                    output.append("0");
                }
                separator = ",\n  ";
            }
            output.append("}");
            return;
        }
        if (expression instanceof ExpressionIs cast) {
            output.append("(");
            print(cast.sub);
            output.append(".Option == ").append(cast.sub.resolvedTypeReference.name).append("Option")
                    .append(cast.checkedType.name).append(")");
            return;
        }
        if (expression instanceof ExpressionConstant cast) {
            output.append(cast.width).append("'h").append(Long.toHexString(cast.value));
            return;
        }
        if (expression instanceof ExpressionBinaryOperator cast) {
            output.append("(");
            print(cast.a);
            output.append(")");
            switch (cast.kind) {
                case Equals:
                    output.append("==");
                    break;
                case NotEquals:
                    output.append("!=");
                    break;
                case Less:
                    output.append("<");
                    break;
                case More:
                    output.append(">");
                    break;
                case LessOrEqual:
                    output.append("<=");
                    break;
                case MoreOrEqual:
                    output.append(">=");
                    break;
                case BooleanAnd:
                    output.append("&");
                    break;
                case BooleanOr:
                case BitwiseOr:
                    output.append("|");
                    break;
                case IntegerSum:
                    output.append("+");
                    break;
                case IntegerDifference:
                    output.append("-");
                    break;
                case IntegerProduct:
                    output.append("*");
                    break;
            }
            output.append("(");
            print(cast.b);
            output.append(")");
            return;
        }
        if (expression instanceof ExpressionPart cast) {
            print(cast.sub);
            if (cast.sub.resolvedTypeReference.resolvedType instanceof OneOfType) {
                output.append(".Value");
            }
            output.append(".").append(cast.name);
            if (cast.sub.resolvedTypeReference.resolvedType instanceof OneOfType) {
                output.append(".Value");
            }
            return;
        }
        throw new RuntimeException("Unhandled case");
    }

    private void printMinMax(ExpressionFunction cast, BiFunction<String, String, String> combineFunction) {
        String prefix = "I" + region.suffixForVariables + "_" + location.suffixForVariables + "_" + cast.id;
        String name = prefix + "_0_" + cast.positionalParameters.length;
        if (emittedExpressions.add(name)) {
            printCombineTree(cast.resolvedTypeReference, prefix, 0, cast.positionalParameters.length,
                    cast.positionalParameters, combineFunction);
        }
        output.append(name);
    }

    private String printCombineTree(TypeReference typeReference, String prefix, int start, int end,
                                    Expression[] parameters, BiFunction<String, String, String> combiner) {
        String intermediateVariableName = prefix + "_" + start + "_" + end;
        module.println(typeReference.toSVString() + " " + intermediateVariableName + ";");
        String result;
        if (end == start + 1) {
            ExpressionPrinter sub = new ExpressionPrinter(module, region, location, emittedExpressions);
            sub.print(parameters[start]);
            result = sub.output.toString();
        } else {
            int middle = (start + end) >> 1;
            String subA = printCombineTree(typeReference, prefix, start, middle, parameters, combiner);
            String subB = printCombineTree(typeReference, prefix, middle, end, parameters, combiner);
            result = combiner.apply(subA, subB);
        }
        module.println("assign " + intermediateVariableName + " = " + result + ";");
        return intermediateVariableName;
    }
}
