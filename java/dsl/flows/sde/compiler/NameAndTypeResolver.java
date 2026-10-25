// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.SourceFilenameAndLocationRange;
import dsl.flows.sde.models.*;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.types.*;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

class NameAndTypeResolver {
    final CompilationElements compilationElements;
    final CompilationErrorList compilationErrorList;
    final TreeMap<String, VariableWithScope> variables = new TreeMap<>();
    final Transform transform;

    NameAndTypeResolver(CompilationElements compilationElements, CompilationErrorList compilationErrorList,
                        Transform transform) {
        this.compilationElements = compilationElements;
        this.compilationErrorList = compilationErrorList;
        this.transform = transform;
    }

    void resolveNamesAndTypeReferences(StatementBlock block) {
        Scope scope = new Scope();
        for (Statement statement : block.statements) {
            resolveNamesAndTypeReferences(statement, scope);
        }
        scope.active = false;
    }

    private void resolveNamesAndTypeReferences(Statement statement, Scope scope) {
        switch (statement) {
            case StatementIfWhile cast -> {
                resolveNamesAndTypeReferences(cast.condition);
                resolveNamesAndTypeReferences(cast.thenBranch);
                if (cast.elseBranch != null) {
                    resolveNamesAndTypeReferences(cast.elseBranch);
                }
                return;
            }
            case StatementVariableDeclaration cast -> {
                if (transform.findPort(cast.name) != null || compilationElements.constants.containsKey(cast.name) ||
                        variables.containsKey(cast.name)) {
                    compilationErrorList.addError("'" + cast.name + "' is already declared", cast.nameLocationRange);
                }
                variables.put(cast.name, new VariableWithScope(cast, scope));
                Compiler.resolveTypeReference(compilationElements, compilationErrorList, cast.typeReference);
                return;
            }
            case StatementExpression cast -> {
                if (cast.assignmentTargetName != null) {
                    NamedEntity assignmentTargetResolved = resolveNamedEntity(cast.assignmentTargetName,
                            cast.assignmentTargetLocationRange);
                    if (assignmentTargetResolved != null) {
                        if (assignmentTargetResolved instanceof VariableOrPort castVariableOrPort) {
                            cast.resolvedAssignmentTarget = castVariableOrPort;
                        } else {
                            compilationErrorList.addError("Constants cannot be assigned", cast.locationRange);
                        }
                    }
                }
                resolveNamesAndTypeReferences(cast.expression);
                if (cast.resolvedAssignmentTarget != null && cast.expression.resolvedTypeReference != null &&
                        cast.expression.resolvedTypeReference.resolvedType != null) {
                    TypeReference assignmentTargetTypeReference = cast.resolvedAssignmentTarget.getTypeReference(
                            compilationElements.BIT_TYPE);
                    Type assignmentTargetTypeResolved = assignmentTargetTypeReference.resolvedType;
                    if (assignmentTargetTypeResolved != null && !(assignmentTargetTypeResolved instanceof BitType) &&
                            assignmentTargetTypeResolved != cast.expression.resolvedTypeReference.resolvedType) {
                        if (assignmentTargetTypeResolved instanceof OneOfType castOneOf &&
                                castOneOf.isOneOfOptions(cast.expression.resolvedTypeReference.resolvedType)) {
                            cast.oneOfTypeWrapper = true;
                            compilationElements.addOneOfWrapper(assignmentTargetTypeReference,
                                    cast.expression.resolvedTypeReference);
                        } else {
                            compilationErrorList.addError("Type of assignment target " + assignmentTargetTypeReference +
                                            " doesn't match type of expression " + cast.expression.resolvedTypeReference,
                                    cast.locationRange);
                        }
                    }
                }
                return;
            }
            case StatementMacroRead cast -> {
                resolveNamesAndTypeReferences(cast.what);
                if (cast.what.resolvedEntity != null) {
                    if (cast.what.resolvedEntity instanceof StatementVariableDeclaration) {
                        cast.portGroupResolved = resolvePortGroupForReadWriteMacros(transform,
                                cast.portGroupLocationRange, cast.portGroupName, cast.what,
                                cast.kind == StatementMacroRead.Kind.VariableLength, false);
                    } else {
                        compilationErrorList.addError("What parameter of Read macro must be a variable",
                                cast.what.locationRange);
                    }
                }
                if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                    resolveNamesAndTypeReferences(cast.complete);
                    if (cast.complete.resolvedEntity != null &&
                            !(cast.complete.resolvedEntity instanceof StatementVariableDeclaration castVariable &&
                                    castVariable.typeReference.resolvedType instanceof BitType &&
                                    castVariable.typeReference.arraySize == null)) {
                        compilationErrorList.addError("Complete parameter must be a Bit variable",
                                cast.complete.locationRange);
                    }
                }
                return;
            }
            case StatementMacroWrite cast -> {
                resolveNamesAndTypeReferences(cast.what);
                cast.portGroupResolved = resolvePortGroupForReadWriteMacros(transform, cast.portGroupLocationRange,
                        cast.portGroupName, cast.what, cast.kind == StatementMacroWrite.Kind.VariableLength, true);
                return;
            }
            case StatementMacroPush cast -> {
                resolveNamesAndTypeReferences(cast.what);
                cast.portGroupResolved = resolvePortGroupForPushPopMacros(cast.portGroupName, cast.locationRange,
                        cast.what, true, Transform.Direction.Output);
                return;
            }
            case StatementMacroPop cast -> {
                resolveNamesAndTypeReferences(cast.what);
                cast.portGroupResolved = resolvePortGroupForPushPopMacros(cast.portGroupName, cast.locationRange,
                        cast.what, false, Transform.Direction.Input);
                return;
            }
            case null, default -> {
            }
        }
        if (statement instanceof StatementStep || statement instanceof StatementLoopControl) {
            return;
        }
        throw new RuntimeException("Unhandled case");
    }

    private Transform.PortGroup resolvePortGroupForPushPopMacros(String portGroupName,
                                                                 SourceFilenameAndLocationRange locationRange,
                                                                 Expression what, boolean wireWithBackpressureOk,
                                                                 Transform.Direction direction) {
        Transform.PortGroup result = transform.findPortGroup(portGroupName);
        if (result == null) {
            compilationErrorList.addError("Port group '" + portGroupName + "' doesn't exist", locationRange);
            return null;
        }
        if (result.connectionKind == null) {
            return null;
        }
        if (result.connectionKind != Connection.Kind.Queue &&
                !(wireWithBackpressureOk && result.connectionKind == Connection.Kind.WireWithBackpressure)) {
            compilationErrorList.addError("Target port group must be connected to a queue" +
                    (wireWithBackpressureOk ? " or wire with backpressure" : ""), locationRange);
            return null;
        }
        if (result.portA.direction != direction) {
            compilationErrorList.addError("Port group '" + portGroupName + "' must be connected as " + direction,
                    result.locationRange);
            return null;
        }
        if (result.connectionKind == Connection.Kind.Queue) {
            if (result.queueNonBlankTypeReference == null) {
                return null;
            }
            compilationElements.addOneOfWrapper(result.typeReference, result.queueNonBlankTypeReference);
        }
        if (what.resolvedTypeReference != null &&
                (result.connectionKind == Connection.Kind.Queue ? result.queueNonBlankTypeReference :
                        result.typeReference).resolvedType != what.resolvedTypeReference.resolvedType) {
            compilationErrorList.addError("'what' parameter must have same type as port", what.locationRange);
            return null;
        }
        return result;
    }

    private void resolveNamesAndTypeReferences(Expression expression) {
        switch (expression) {
            case ExpressionConstant cast -> {
                cast.resolvedTypeReference = new TypeReference(null, "Bit", compilationElements.BIT_TYPE, 64);
                return;
            }
            case ExpressionIs cast -> {
                resolveNamesAndTypeReferences(cast.sub);
                Compiler.resolveTypeReference(compilationElements, compilationErrorList, cast.checkedType);
                if (cast.sub.resolvedTypeReference != null && cast.sub.resolvedTypeReference.resolvedType != null &&
                        cast.checkedType.resolvedType != null) {
                    if (cast.sub.resolvedTypeReference.resolvedType instanceof OneOfType castOneOf) {
                        if (!castOneOf.isOneOfOptions(cast.checkedType.resolvedType)) {
                            StringBuilder builder = new StringBuilder().append("Type '").append(cast.checkedType.name)
                                    .append("' is not one of the options of type '")
                                    .append(cast.sub.resolvedTypeReference.name).append("'. Valid options are");
                            for (TypeReference option : castOneOf.options) {
                                builder.append(" ").append(option.name);
                            }
                            compilationErrorList.addError(builder.toString(), cast.locationRange);
                        }
                    } else {
                        compilationErrorList.addError("Is expression requires oneof type", cast.locationRange);
                    }
                }
                cast.resolvedTypeReference = new TypeReference(null, "Bit", compilationElements.BIT_TYPE, null);
                return;
            }
            case ExpressionPart cast -> {
                resolveNamesAndTypeReferences(cast.sub);
                TypeReference typeReference = null;
                if (cast.sub.resolvedTypeReference != null) {
                    if (cast.sub.resolvedTypeReference.resolvedType instanceof OneOfType castOneOfType) {
                        for (TypeReference option : castOneOfType.options) {
                            if (option.name.equals(cast.name)) {
                                typeReference = option;
                                break;
                            }
                        }
                        if (typeReference == null) {
                            compilationErrorList.addError(
                                    "Type '" + cast.name + "' is not one of the options in type '" +
                                            cast.sub.resolvedTypeReference.name + "'", cast.locationRange);
                        }
                    } else if (cast.sub.resolvedTypeReference.resolvedType instanceof StructType castSubType) {
                        StructType.Field field = castSubType.findField(cast.name);
                        if (field != null) {
                            typeReference = field.type();
                        } else {
                            compilationErrorList.addError(
                                    "No field '" + cast.name + "' in type '" + cast.sub.resolvedTypeReference.name +
                                            "'", cast.locationRange);
                        }
                    } else {
                        compilationErrorList.addError("Part expression requires either a oneof or struct type",
                                cast.locationRange);
                    }
                    cast.resolvedTypeReference = typeReference;
                }
                return;
            }
            case ExpressionObject cast -> {
                Compiler.resolveTypeReference(compilationElements, compilationErrorList, cast.resolvedTypeReference);
                if (cast.resolvedTypeReference.resolvedType instanceof StructType castStruct) {
                    for (Map.Entry<String, FieldOrParameter> field : cast.fields.entrySet()) {
                        if (castStruct.findField(field.getKey()) == null) {
                            compilationErrorList.addError(
                                    "No field '" + field.getKey() + "' in type " + cast.resolvedTypeReference.name,
                                    field.getValue().keyLocationRange());
                        }
                        resolveNamesAndTypeReferences(field.getValue().expression());
                    }
                } else {
                    compilationErrorList.addError("Object expression must refer to struct type", cast.locationRange);
                }
                return;
            }
            case ExpressionFunction cast -> {
                for (Expression parameter : cast.positionalParameters) {
                    resolveNamesAndTypeReferences(parameter);
                }
                for (Map.Entry<String, FieldOrParameter> entry : cast.namedParameters.entrySet()) {
                    resolveNamesAndTypeReferences(entry.getValue().expression());
                }
                switch (cast.name) {
                    case "Concatenate" -> {
                        if (cast.positionalParameters.length == 0 || !cast.namedParameters.isEmpty()) {
                            compilationErrorList.addError(
                                    "Concatenate function takes at least one positional parameter and no named parameters",
                                    cast.locationRange);
                        }
                    }
                    case "If" -> {
                        FieldOrParameter condition = cast.namedParameters.get("condition");
                        FieldOrParameter thenValue = cast.namedParameters.get("thenValue");
                        FieldOrParameter elseValue = cast.namedParameters.get("elseValue");
                        if (cast.positionalParameters.length != 0 || condition == null || thenValue == null ||
                                elseValue == null) {
                            compilationErrorList.addError(
                                    "If function requires no positional parameters and three named parameters: 'condition', 'thenValue' and 'elseValue'",
                                    cast.locationRange);
                        }
                    }
                    case "Slice" -> {
                        FieldOrParameter whole = cast.namedParameters.get("whole");
                        FieldOrParameter offsetInBits = cast.namedParameters.get("offsetInBits");
                        FieldOrParameter countInBits = cast.namedParameters.get("countInBits");
                        if (cast.positionalParameters.length != 0 || whole == null || offsetInBits == null ||
                                countInBits == null) {
                            compilationErrorList.addError(
                                    "Slice function requires no positional parameters and three named parameters: 'whole', 'offsetInBits' and 'countInBits'",
                                    cast.locationRange);
                        }
                    }
                    case "ShiftLeft", "ShiftRight" -> {
                        FieldOrParameter whole = cast.namedParameters.get("what");
                        FieldOrParameter offsetInBits = cast.namedParameters.get("howManyBits");
                        if (cast.positionalParameters.length != 0 || whole == null || offsetInBits == null) {
                            compilationErrorList.addError(cast.name +
                                            " function requires no positional parameters and two named parameters: 'what' and 'howManyBits'",
                                    cast.locationRange);
                        }
                    }
                    case "Max", "Min" -> resolveMaxMinInternetChecksum(cast, false);
                    case "InternetChecksum" -> resolveMaxMinInternetChecksum(cast, true);
                    default ->
                            compilationErrorList.addError("Unknown function '" + cast.name + "'", cast.locationRange);
                }
                return;
            }
            case ExpressionBinaryOperator cast -> {
                resolveNamesAndTypeReferences(cast.a);
                resolveNamesAndTypeReferences(cast.b);
                if (cast.a.resolvedTypeReference != null && cast.b.resolvedTypeReference != null) {
                    switch (cast.kind) {
                        case BooleanAnd:
                        case BooleanOr:
                            if (cast.a.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.a.resolvedTypeReference.arraySize == null &&
                                    cast.b.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.b.resolvedTypeReference.arraySize == null) {
                                cast.resolvedTypeReference = new TypeReference(null, "Bit",
                                        compilationElements.BIT_TYPE, null);
                            } else {
                                compilationErrorList.addError(
                                        "Operands to " + cast.kind + " operator must be of type Bit",
                                        cast.locationRange);
                            }
                            break;
                        case Equals:
                        case NotEquals:
                        case Less:
                        case More:
                        case LessOrEqual:
                        case MoreOrEqual:
                            if (cast.a.resolvedTypeReference.resolvedType !=
                                    cast.b.resolvedTypeReference.resolvedType ||
                                    !(cast.a.resolvedTypeReference.resolvedType instanceof BitType ||
                                            Objects.equals(cast.a.resolvedTypeReference.arraySize,
                                                    cast.b.resolvedTypeReference.arraySize))) {
                                compilationErrorList.addError(
                                        "Operands to " + cast.kind + " operator must be of the same type",
                                        cast.locationRange);
                            }
                            cast.resolvedTypeReference = new TypeReference(null, "Bit", compilationElements.BIT_TYPE,
                                    null);
                            break;
                        case IntegerProduct:
                            if (cast.a.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.a.resolvedTypeReference.arraySize != null &&
                                    cast.b.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.b.resolvedTypeReference.arraySize != null) {
                                cast.resolvedTypeReference = new TypeReference(null, "Bit",
                                        compilationElements.BIT_TYPE, cast.a.resolvedTypeReference.arraySize +
                                        cast.b.resolvedTypeReference.arraySize);
                            } else {
                                compilationErrorList.addError("Integer product operator requires two Bit arrays",
                                        cast.locationRange);
                            }
                            break;
                        case IntegerSum:
                        case IntegerDifference:
                        case BitwiseOr:
                            if (cast.a.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.a.resolvedTypeReference.arraySize != null &&
                                    cast.b.resolvedTypeReference.resolvedType instanceof BitType &&
                                    cast.b.resolvedTypeReference.arraySize != null) {
                                cast.resolvedTypeReference = new TypeReference(null, "Bit",
                                        compilationElements.BIT_TYPE, Math.max(cast.a.resolvedTypeReference.arraySize,
                                        cast.b.resolvedTypeReference.arraySize) +
                                        (cast.kind == ExpressionBinaryOperator.Kind.BitwiseOr ? 0 : 1));
                            } else {
                                compilationErrorList.addError(cast.kind + " operator requires two Bit arrays",
                                        cast.locationRange);
                            }
                            break;
                    }
                }
                return;
            }
            case ExpressionName cast -> {
                NamedEntity resolvedEntity = resolveNamedEntity(cast.name, cast.locationRange);
                if (resolvedEntity != null) {
                    cast.resolvedEntity = resolvedEntity;
                    cast.resolvedTypeReference = resolvedEntity.getTypeReference(compilationElements.BIT_TYPE);
                }
                return;
            }
            case ExpressionUnaryOperator cast -> {
                resolveNamesAndTypeReferences(cast.sub);
                if (cast.sub.resolvedTypeReference != null) {
                    if (cast.sub.resolvedTypeReference.resolvedType instanceof BitType &&
                            (cast.sub.resolvedTypeReference.arraySize == null) ==
                                    (cast.kind == ExpressionUnaryOperator.Kind.LogicalNot)) {
                        cast.resolvedTypeReference = cast.sub.resolvedTypeReference;
                    } else {
                        compilationErrorList.addError(cast.kind + " operator takes " +
                                (cast.kind == ExpressionUnaryOperator.Kind.LogicalNot ? "bit" : "bit array") +
                                " as its operand", cast.locationRange);
                    }
                }
                return;
            }
            case null, default -> {
            }
        }
        throw new RuntimeException("Unhandled case");
    }

    private void resolveMaxMinInternetChecksum(ExpressionFunction cast, boolean resultBit16) {
        if (cast.positionalParameters.length == 0) {
            compilationErrorList.addError(cast.name + " function takes at least one positional parameters",
                    cast.locationRange);
            return;
        }
        TypeReference widest = null;
        for (Expression parameter : cast.positionalParameters) {
            if (parameter.resolvedTypeReference != null && parameter.resolvedTypeReference.resolvedType != null) {
                if (parameter.resolvedTypeReference.resolvedType instanceof BitType &&
                        parameter.resolvedTypeReference.arraySize != null) {
                    if (widest == null || parameter.resolvedTypeReference.arraySize > widest.arraySize) {
                        widest = parameter.resolvedTypeReference;
                    }
                } else {
                    compilationErrorList.addError("Parameters to " + cast.name + " must be arrays of Bit",
                            parameter.locationRange);
                }
            }
        }
        cast.resolvedTypeReference =
                resultBit16 ? new TypeReference(null, "Bit", compilationElements.BIT_TYPE, 16) : widest;
    }

    private NamedEntity resolveNamedEntity(String name, SourceFilenameAndLocationRange locationRange) {
        ConstantDeclaration constantDeclaration = compilationElements.constants.get(name);
        if (constantDeclaration != null) {
            return constantDeclaration;
        }
        Transform.Port port = transform.findPort(name);
        if (port != null) {
            return port;
        }
        VariableWithScope variableWithScope = variables.get(name);
        if (variableWithScope == null) {
            compilationErrorList.addError("Constant, port or variable '" + name + "' is not found", locationRange);
            return null;
        }
        if (!variableWithScope.scope().active) {
            compilationErrorList.addError("Variable '" + name + "' is out of scope", locationRange);
            return null;
        }
        return variableWithScope.variable();
    }

    private Transform.PortGroup resolvePortGroupForReadWriteMacros(Transform transform,
                                                                   SourceFilenameAndLocationRange portGroupLocationRange,
                                                                   String portGroupName, Expression what,
                                                                   boolean variableLength, boolean isWrite) {
        Transform.PortGroup portGroupResolved = transform.findPortGroup(portGroupName);
        Transform.PortGroup result = null;
        if (portGroupResolved == null) {
            compilationErrorList.addError("Port group '" + portGroupName + "' doesn't exist", portGroupLocationRange);
        } else if (portGroupResolved.portA == null) {
        } else if ((portGroupResolved.portA.direction == Transform.Direction.Output) != isWrite) {
            compilationErrorList.addError(
                    "Port group '" + portGroupName + "' should be connected as " + (isWrite ? "source" : "destination"),
                    portGroupLocationRange);
        } else if (portGroupResolved.portA.typeReference.resolvedType instanceof StructType castStruct) {
            StructType.Field dataField = castStruct.findField("Data");
            if (dataField == null) {
                compilationErrorList.addError("Port group '" + portGroupName + "' does not have a Data field",
                        portGroupLocationRange);
            } else {
                int dataFieldSizeInBits = dataField.type().totalBits();
                if ((dataFieldSizeInBits & 7) == 0) {
                    result = portGroupResolved;
                    result.dataFieldByteCount = dataFieldSizeInBits >> 3;
                } else {
                    compilationErrorList.addError(
                            "Port group '" + portGroupName + "' Data field is not multiple of 8 bits",
                            portGroupLocationRange);
                }
            }
        } else {
            compilationErrorList.addError("Port group '" + portGroupName + "' should have struct type",
                    portGroupLocationRange);
        }
        if (what.resolvedTypeReference != null) {
            int whatSizeInBits;
            if (variableLength) {
                if (what.resolvedTypeReference.resolvedType instanceof StructType castStruct) {
                    StructType.Field whatDataField = castStruct.findField("Data");
                    if (whatDataField == null) {
                        compilationErrorList.addError("'what' parameter must be of struct type with Data field",
                                what.locationRange);
                        return null;
                    }
                    if (whatDataField.type().resolvedType == null ||
                            whatDataField.type().resolvedType.totalBits == null) {
                        return null;
                    }
                    whatSizeInBits = whatDataField.type().totalBits();
                } else {
                    compilationErrorList.addError("'what' parameter must be of struct type", what.locationRange);
                    return null;
                }
            } else {
                if (what.resolvedTypeReference.resolvedType == null ||
                        what.resolvedTypeReference.resolvedType.totalBits == null) {
                    return null;
                }
                whatSizeInBits = what.resolvedTypeReference.totalBits();
            }
            if ((whatSizeInBits & 7) != 0) {
                compilationErrorList.addError("'what' parameter must refer to expression that is multiple of 8 bits",
                        what.locationRange);
            } else if (result != null && isWrite) {
                result.largestWriteWhatByteCount = Math.max(result.largestWriteWhatByteCount, whatSizeInBits >> 3);
            }
        }
        return result;
    }
}
