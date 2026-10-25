// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.compiler;

import dsl.flows.sde.MacroBuilder;
import dsl.flows.sde.models.Connection;
import dsl.flows.sde.models.Transform;
import dsl.flows.sde.models.expressions.*;
import dsl.flows.sde.models.statements.*;
import dsl.flows.sde.models.types.BitType;
import dsl.flows.sde.models.types.StructType;
import dsl.flows.sde.models.types.TypeReference;

import java.util.LinkedList;
import java.util.TreeMap;

public class MacroReplacer {
    static void replaceMacros(StatementBlock block, Transform transform, BitType bitType, StructType blankType) {
        LinkedList<Statement> newStatements = new LinkedList<>();
        for (Statement statement : block.statements) {
            switch (statement) {
                case StatementMacroPush cast -> {
                    if (cast.portGroupResolved == null || cast.portGroupResolved.portA == null) {
                        break;
                    }
                    StatementExpression assign = new StatementExpression(statement.locationRange,
                            cast.portGroupResolved.portA, cast.what, "assign", transform.idGenerator.generateId());
                    assign.oneOfTypeWrapper = cast.portGroupResolved.connectionKind == Connection.Kind.Queue;
                    if (cast.kind == StatementMacroPush.Kind.Asserting) {
                        newStatements.add(assign);
                    } else if (cast.portGroupResolved.connectionKind == Connection.Kind.WireWithBackpressure) {
                        LinkedList<Statement> whileThenBranch = new LinkedList<>();
                        whileThenBranch.add(
                                new StatementStep(statement.locationRange, "step", transform.idGenerator.generateId()));
                        whileThenBranch.add(assign);
                        LinkedList<Statement> ifThenBranch = new LinkedList<>();
                        ifThenBranch.add(
                                new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break,
                                        "break", transform.idGenerator.generateId()));
                        whileThenBranch.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                                new ExpressionName(statement.locationRange, cast.portGroupResolved.portB, bitType,
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, ifThenBranch), null, "if",
                                transform.idGenerator.generateId()));
                        newStatements.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.While,
                                new ExpressionConstant(statement.locationRange, 1, 1,
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, whileThenBranch), null, "while",
                                transform.idGenerator.generateId()));
                    } else if (cast.portGroupResolved.connectionKind == Connection.Kind.Queue) {
                        LinkedList<Statement> whileThenBranch = new LinkedList<>();
                        LinkedList<Statement> readyBranch = new LinkedList<>();
                        readyBranch.add(assign);
                        readyBranch.add(new StatementStep(statement.locationRange, "stepReady",
                                transform.idGenerator.generateId()));
                        readyBranch.add(
                                new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break,
                                        "break", transform.idGenerator.generateId()));
                        LinkedList<Statement> notReadyBranch = new LinkedList<>();
                        notReadyBranch.add(new StatementStep(statement.locationRange, "stepNotReady",
                                transform.idGenerator.generateId()));
                        whileThenBranch.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                                new ExpressionName(statement.locationRange, cast.portGroupResolved.portB, bitType,
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, readyBranch),
                                new StatementBlock(statement.locationRange, notReadyBranch), "if",
                                transform.idGenerator.generateId()));
                        newStatements.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.While,
                                new ExpressionConstant(statement.locationRange, 1, 1,
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, whileThenBranch), null, "while",
                                transform.idGenerator.generateId()));
                    } else {
                        throw new RuntimeException("Unknown connection kind: " + cast.portGroupResolved.connectionKind);
                    }
                }
                case StatementMacroPop cast -> {
                    if (cast.portGroupResolved == null || cast.portGroupResolved.portA == null ||
                            cast.portGroupResolved.portA.typeReference.resolvedType == null ||
                            cast.what.resolvedEntity == null) {
                        break;
                    }
                    StatementExpression assign1 = new StatementExpression(statement.locationRange,
                            cast.portGroupResolved.portB,
                            new ExpressionConstant(statement.locationRange, 1, 1, transform.idGenerator.generateId()),
                            "assignPop", transform.idGenerator.generateId());
                    StatementExpression assign2 = new StatementExpression(statement.locationRange,
                            (StatementVariableDeclaration) cast.what.resolvedEntity,
                            new ExpressionPart(statement.locationRange,
                                    new ExpressionName(statement.locationRange, cast.portGroupResolved.portA, bitType,
                                            transform.idGenerator.generateId()),
                                    Compiler.getNonBlankType(cast.portGroupResolved.portA.typeReference.resolvedType,
                                            blankType).name, transform.idGenerator.generateId()), "assignWhat",
                            transform.idGenerator.generateId());
                    if (cast.kind == StatementMacroPop.Kind.Asserting) {
                        newStatements.add(assign1);
                        newStatements.add(assign2);
                    } else if (cast.portGroupResolved.connectionKind == Connection.Kind.Queue) {
                        LinkedList<Statement> whileThenBranch = new LinkedList<>();
                        LinkedList<Statement> ifNotBlankBranch = new LinkedList<>();
                        ifNotBlankBranch.add(assign1);
                        ifNotBlankBranch.add(assign2);
                        ifNotBlankBranch.add(new StatementStep(statement.locationRange, "stepNotBlank",
                                transform.idGenerator.generateId()));
                        ifNotBlankBranch.add(
                                new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break,
                                        "break", transform.idGenerator.generateId()));
                        LinkedList<Statement> ifBlankBranch = new LinkedList<>();
                        ifBlankBranch.add(new StatementStep(statement.locationRange, "stepBlank",
                                transform.idGenerator.generateId()));
                        whileThenBranch.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                                new ExpressionIs(statement.locationRange,
                                        new ExpressionName(statement.locationRange, cast.portGroupResolved.portA,
                                                bitType, transform.idGenerator.generateId()),
                                        new TypeReference(cast.locationRange, "Blank", blankType, null),
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, ifBlankBranch),
                                new StatementBlock(statement.locationRange, ifNotBlankBranch), "if",
                                transform.idGenerator.generateId()));
                        newStatements.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.While,
                                new ExpressionConstant(statement.locationRange, 1, 1,
                                        transform.idGenerator.generateId()),
                                new StatementBlock(statement.locationRange, whileThenBranch), null, "while",
                                transform.idGenerator.generateId()));
                    } else {
                        throw new RuntimeException("Unknown connection kind: " + cast.portGroupResolved.connectionKind);
                    }
                }
                case StatementMacroWrite cast -> {
                /*
                    N is bus width in bytes
                    M is this what in bytes
                    O is largest what in bytes
                    bit[...] WriteAccumulatorValue;
                    bit[...] WriteAccumulatorByteCount;
                    bit WriteAccumulatorLast;
                    ...
                    WriteAccumulatorValue = {WriteAccumulatorByteCount > 0 ? WriteAccumulatorValue[(N-1)*8+:8] : what[(WriteAccumulatorByteCount+M-1)*8+:8],
                        WriteAccumulatorByteCount > 1 ? WriteAccumulatorValue[(N-2)*8+:8] : what[(WriteAccumulatorByteCount+M-2)*8+:8],
                        WriteAccumulatorByteCount > 2 ? WriteAccumulatorValue[(N-3)*8+:8] : what[(WriteAccumulatorByteCount+M-3)*8+:8],
                        WriteAccumulatorByteCount > 3 ? WriteAccumulatorValue[(N-4)*8+:8] : what[(WriteAccumulatorByteCount+M-4)*8+:8],
                        what[(WriteAccumulatorByteCount+M-5)*8+:8],
                        what[(WriteAccumulatorByteCount+M-6)*8+:8],
                        ...};
                    WriteAccumulatorByteCount = WriteAccumulatorByteCount + what.ByteCount;
                    WriteAccumulatorLast = kind == VariableLength ? what.Last : 0;
                    while (true) {
                      WhereWord = {Data: Slice(whole: WriteAccumulatorValue, offsetInBits: O*8, countInBits: N*8),
                        ByteCount: Min(WriteAccumulatorByteCount, N), Last: WriteAccumulatorLast & WriteAccumulatorByteCount <= N};
                      if (WhereReady) {
                        WriteAccumulatorValue = Concatenate(Slice(whole: WriteAccumulatorValue, offsetInBits: 0, countInBits: O*8), Zero(width: N*8));
                        WriteAccumulatorByteCount = WriteAccumulatorByteCount <= N ? 0 : WriteAccumulatorByteCount-N;
                      }
                      step;
                      if (WriteAccumulatorLast ? (WriteAccumulatorByteCount == 0) : (WriteAccumulatorByteCount < N)) {
                        break;
                      }
                    }
                 */
                    if (cast.portGroupResolved == null || cast.what.resolvedTypeReference == null ||
                            cast.what.resolvedTypeReference.resolvedType == null ||
                            cast.what.resolvedTypeReference.resolvedType.totalBits == null) {
                        break;
                    }
                    int whatSizeInBytes = cast.what.resolvedTypeReference.totalBits() >> 3;
                    MacroBuilder builder = new MacroBuilder(bitType, transform, statement.locationRange);
                    Expression[] concatenateParameters = new Expression[cast.portGroupResolved.dataFieldByteCount +
                            cast.portGroupResolved.largestWriteWhatByteCount];
                    for (int index = 0; index < concatenateParameters.length; index++) {
                        Expression nextByte;
                        if (index < whatSizeInBytes + cast.portGroupResolved.dataFieldByteCount) {
                            Expression data = cast.what;
                            if (cast.kind == StatementMacroWrite.Kind.VariableLength) {
                                data = builder.part(data, "Data");
                            }
                            nextByte = builder.slice(data, builder.concatenate(builder.sum(
                                            builder.constant(whatSizeInBytes - 1 - index,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize),
                                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable)),
                                    builder.constant(0, 3)), builder.constant(8, 4));
                        } else {
                            nextByte = builder.constant(0, 8);
                        }
                        if (index < cast.portGroupResolved.dataFieldByteCount) {
                            nextByte = builder.ifte(
                                    builder.more(builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(index,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize)),
                                    builder.slice(builder.name(cast.portGroupResolved.writeAccumulatorValueVariable),
                                            builder.constant(
                                                    cast.portGroupResolved.writeAccumulatorValueVariable.typeReference.arraySize -
                                                            ((1 + index) << 3),
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize +
                                                            3), builder.constant(8, 4)), nextByte);
                        }
                        concatenateParameters[index] = nextByte;
                    }
                    newStatements.add(builder.expression(cast.portGroupResolved.writeAccumulatorValueVariable,
                            builder.concatenate(concatenateParameters), "shiftWhatIn"));
                    newStatements.add(builder.expression(cast.portGroupResolved.writeAccumulatorByteCountVariable,
                            builder.sum(builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                    cast.kind == StatementMacroWrite.Kind.FixedLength ?
                                            builder.constant(whatSizeInBytes,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize) :
                                            builder.part(cast.what, "ByteCount")), "increaseByteCount"));
                    newStatements.add(builder.expression(cast.portGroupResolved.writeAccumulatorLastVariable,
                            cast.kind == StatementMacroWrite.Kind.FixedLength ? builder.constant(0, 1) :
                                    builder.part(cast.what, "Last"), "setLast"));
                    LinkedList<Statement> whileBody = new LinkedList<>();
                    TreeMap<String, FieldOrParameter> portGroupWordFields = new TreeMap<>();
                    portGroupWordFields.put("Data", new FieldOrParameter(null,
                            builder.slice(builder.name(cast.portGroupResolved.writeAccumulatorValueVariable),
                                    builder.constant(cast.portGroupResolved.largestWriteWhatByteCount << 3,
                                            cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize +
                                                    3), builder.constant(cast.portGroupResolved.dataFieldByteCount << 3,
                                            cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize +
                                                    3))));
                    portGroupWordFields.put("ByteCount", new FieldOrParameter(null, builder.min(
                            new TypeReference(statement.locationRange, "Bit", bitType,
                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize),
                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                            builder.constant(cast.portGroupResolved.dataFieldByteCount,
                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize))));
                    portGroupWordFields.put("Last", new FieldOrParameter(null,
                            builder.and(builder.name(cast.portGroupResolved.writeAccumulatorLastVariable),
                                    builder.lessOrEqual(
                                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(cast.portGroupResolved.dataFieldByteCount,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize)))));
                    whileBody.add(builder.expression(cast.portGroupResolved.portA,
                            new ExpressionObject(statement.locationRange, cast.portGroupResolved.portA.typeReference,
                                    portGroupWordFields, transform.idGenerator.generateId()), "assignPortGroupWord"));
                    LinkedList<Statement> ifReady = new LinkedList<>();
                    ifReady.add(builder.expression(cast.portGroupResolved.writeAccumulatorValueVariable,
                            builder.concatenate(
                                    builder.slice(builder.name(cast.portGroupResolved.writeAccumulatorValueVariable),
                                            builder.constant(0, 1),
                                            builder.constant(cast.portGroupResolved.largestWriteWhatByteCount << 3,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize +
                                                            3)),
                                    builder.constant(0, cast.portGroupResolved.dataFieldByteCount << 3)),
                            "shiftWordOut"));
                    ifReady.add(builder.expression(cast.portGroupResolved.writeAccumulatorByteCountVariable,
                            builder.ifte(builder.lessOrEqual(
                                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(cast.portGroupResolved.dataFieldByteCount,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize)),
                                    builder.constant(0, 1), builder.difference(
                                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(cast.portGroupResolved.dataFieldByteCount,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize))),
                            "decreaseByteCount"));
                    whileBody.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                            builder.name(cast.portGroupResolved.portB),
                            new StatementBlock(statement.locationRange, ifReady), null, "ifReady",
                            transform.idGenerator.generateId()));
                    whileBody.add(
                            new StatementStep(statement.locationRange, "step", transform.idGenerator.generateId()));
                    LinkedList<Statement> breakIfBody = new LinkedList<>();
                    breakIfBody.add(
                            new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break, "break",
                                    transform.idGenerator.generateId()));
                    whileBody.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                            builder.ifte(builder.name(cast.portGroupResolved.writeAccumulatorLastVariable),
                                    builder.equal(
                                            builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(0,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize)),
                                    builder.less(builder.name(cast.portGroupResolved.writeAccumulatorByteCountVariable),
                                            builder.constant(cast.portGroupResolved.dataFieldByteCount,
                                                    cast.portGroupResolved.writeAccumulatorByteCountVariable.typeReference.arraySize))),
                            new StatementBlock(statement.locationRange, breakIfBody), null, "breakIf",
                            transform.idGenerator.generateId()));
                    newStatements.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.While,
                            builder.constant(1, 1), new StatementBlock(statement.locationRange, whileBody), null,
                            "while", transform.idGenerator.generateId()));
                }
                case StatementMacroRead cast -> {
                /*
                    N is bus width in bytes
                    M is this what in bytes
                    O is largest what in bytes
                    bit[...] ReadBusPosition = 0;
                    ...
                    data = {
                        Where.Data[(N-1-ReadBusPosition)*8+:8],
                        Where.Data[(N-2-ReadBusPosition)*8+:8],
                        ...,
                        {(M-N)*8'{0}}};
                    if constexpr (Kind == FixedLength) {
                      what = data;
                      bit[...] ReadWhatPosition = Where.ByteCount-ReadBusPosition;
                      bit Last = Where.Last;
                    } else {
                      what = WhatType{
                        Data: data,
                        ByteCount: Where.ByteCount-ReadBusPosition,
                        Last: Where.Last};
                    }
                    while (true) {
                      step;
                      if constexpr (Kind == FixedLength) {
                        if (Last) {
                          ReadBusPosition = 0;
                          complete = false;
                          break;
                        }
                        ReadBusPosition = Min(M-ReadWhatPosition, Where.ByteCount);
                        what = {
                            ReadWhatPosition == 0 ? Where.Data[((N-1) % N)*8+:8] : what[(M-1)*8+:8],
                            ReadWhatPosition <= 1 ? Where.Data[((N-2+ReadWhatPosition) % N)*8+:8] : what[(M-2)*8+:8],
                            ...,
                            ReadWhatPosition >= 1 && ReadWhatPosition <= N ? Where.Data[((-1+ReadWhatPosition) % N)*8+:8] : what[(M-1-N)*8+:8],
                            ...
                            ReadWhatPosition >= M-N ? Where.Data[((N-M+ReadWhatPosition) % N)*8+:8] : what[0+:8]};
                        ReadWhatPosition = ReadWhatPosition+ReadBusPosition;
                        Last = (ReadBusPosition == Where.ByteCount) & Where.Last;
                        if (ReadWhatPosition == M) {
                          if (Last) {
                            ReadBusPosition = 0;
                          }
                          complete = true;
                          break;
                        }
                      } else {
                        if (what.Last) {
                          ReadBusPosition = 0;
                          break;
                        }
                        ReadBusPosition = Min(M-what.ByteCount, Where.ByteCount);
                        what = WhatType{Data: {
                            what.ByteCount == 0 ? Where.Data[((N-1) % N)*8+:8] : what.Data[(M-1)*8+:8],
                            what.ByteCount <= 1 ? Where.Data[((N-2+what.ByteCount) % N)*8+:8] : what.Data[(M-2)*8+:8],
                            ...,
                            what.ByteCount >= 1 && what.ByteCount <= N ? Where.Data[((-1+what.ByteCount) % N)*8+:8] : what.Data[(M-1-N)*8+:8],
                            ...
                            what.ByteCount >= M-N ? Where.Data[((N-M+what.ByteCount) % N)*8+:8] : what.Data[0+:8]},
                          ByteCount: what.ByteCount + ReadBusPosition,
                          Last: (ReadBusPosition == Where.ByteCount) & Where.Last};
                        if (what.ByteCount == M) {
                          if (what.Last) {
                            ReadBusPosition = 0;
                          }
                          break;
                        }
                      }
                    }
                 */
                    if (cast.portGroupResolved == null || cast.what.resolvedEntity == null ||
                            (cast.kind == StatementMacroRead.Kind.FixedLength &&
                                    cast.complete.resolvedEntity == null) || cast.what.resolvedTypeReference == null ||
                            cast.what.resolvedTypeReference.resolvedType == null ||
                            cast.what.resolvedTypeReference.resolvedType.totalBits == null) {
                        break;
                    }
                    StatementVariableDeclaration castVariable;
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        if (!(cast.complete.resolvedEntity instanceof StatementVariableDeclaration castVariableSub)) {
                            break;
                        }
                        castVariable = castVariableSub;
                    } else {
                        castVariable = null;
                    }
                    int whatSizeInBytes = cast.what.resolvedTypeReference.totalBits() >> 3;
                    MacroBuilder builder = new MacroBuilder(bitType, transform, statement.locationRange);
                    StatementVariableDeclaration readWhatPosition;
                    StatementVariableDeclaration last;
                    Expression[] initialConcatenateParameters = new Expression[
                            cast.portGroupResolved.dataFieldByteCount + 1];
                    for (int index = 0; index < cast.portGroupResolved.dataFieldByteCount; index++) {
                        initialConcatenateParameters[index] = builder.slice(
                                builder.part(builder.name(cast.portGroupResolved.portA), "Data"), builder.concatenate(
                                        builder.difference(
                                                builder.constant(cast.portGroupResolved.dataFieldByteCount - 1 - index,
                                                        cast.portGroupResolved.readBusPosition.typeReference.arraySize),
                                                builder.name(cast.portGroupResolved.readBusPosition)),
                                        builder.constant(0, 3)), builder.constant(8, 4));
                    }
                    initialConcatenateParameters[cast.portGroupResolved.dataFieldByteCount] = builder.constant(0,
                            (whatSizeInBytes - cast.portGroupResolved.dataFieldByteCount) << 3);
                    Expression initialChunkValue = builder.concatenate(initialConcatenateParameters);
                    Expression chunk = builder.difference(
                            builder.part(builder.name(cast.portGroupResolved.portA), "ByteCount"),
                            builder.name(cast.portGroupResolved.readBusPosition));
                    Expression whereLast = builder.part(builder.name(cast.portGroupResolved.portA), "Last");
                    int whatPositionBits = 32 - Integer.numberOfLeadingZeros(whatSizeInBytes);
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        readWhatPosition = new StatementVariableDeclaration(statement.locationRange, cast.locationRange,
                                new TypeReference(statement.locationRange, "Bit", bitType, whatPositionBits), null,
                                "ReadWhatPosition", transform.idGenerator.generateId());
                        last = new StatementVariableDeclaration(statement.locationRange, cast.locationRange,
                                new TypeReference(statement.locationRange, "Bit", bitType, null), null, "Last",
                                transform.idGenerator.generateId());
                        transform.variablesOrPorts.add(readWhatPosition);
                        transform.variablesOrPorts.add(last);
                        newStatements.add(builder.expression((StatementVariableDeclaration) cast.what.resolvedEntity,
                                initialChunkValue, "initializeWhat"));
                        newStatements.add(builder.expression(readWhatPosition, chunk, "initializeReadWhatPosition"));
                        newStatements.add(builder.expression(last, whereLast, "initializeLast"));
                    } else {
                        TreeMap<String, FieldOrParameter> fields = new TreeMap<>();
                        fields.put("Data", new FieldOrParameter(null, initialChunkValue));
                        fields.put("ByteCount", new FieldOrParameter(null, chunk));
                        fields.put("Last", new FieldOrParameter(null, whereLast));
                        newStatements.add(builder.expression((StatementVariableDeclaration) cast.what.resolvedEntity,
                                new ExpressionObject(statement.locationRange, cast.what.resolvedTypeReference, fields,
                                        transform.idGenerator.generateId()), "initializeWhat"));
                        readWhatPosition = null;
                        last = null;
                    }
                    LinkedList<Statement> whileBody = new LinkedList<>();
                    whileBody.add(
                            new StatementStep(statement.locationRange, "step", transform.idGenerator.generateId()));
                    LinkedList<Statement> ifLastBody = new LinkedList<>();
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        ifLastBody.add(builder.expression(castVariable, builder.constant(0, 1), "assignCompleteFalse"));
                    }
                    ifLastBody.add(builder.expression(cast.portGroupResolved.readBusPosition,
                            builder.constant(0, cast.portGroupResolved.readBusPosition.typeReference.arraySize),
                            "lastResetReadBusPosition"));
                    ifLastBody.add(new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break,
                            "ifLastBreak", transform.idGenerator.generateId()));
                    whileBody.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                            cast.kind == StatementMacroRead.Kind.FixedLength ? builder.name(last) :
                                    builder.part(builder.name((StatementVariableDeclaration) cast.what.resolvedEntity),
                                            "Last"), new StatementBlock(statement.locationRange, ifLastBody), null,
                            "ifLast", transform.idGenerator.generateId()));
                    whileBody.add(builder.expression(cast.portGroupResolved.readBusPosition,
                            builder.min(new TypeReference(statement.locationRange, "Bit", bitType, whatPositionBits),
                                    builder.difference(builder.constant(whatSizeInBytes, whatPositionBits),
                                            getWhatPosition(cast, builder, readWhatPosition)),
                                    builder.part(builder.name(cast.portGroupResolved.portA), "ByteCount")),
                            "setReadBusPosition"));
                    Expression[] concatenateParameters = new Expression[whatSizeInBytes];
                    for (int index = 0; index < whatSizeInBytes; index++) {
                        ExpressionBinaryOperator le = index < whatSizeInBytes - 1 ?
                                builder.lessOrEqual(getWhatPosition(cast, builder, readWhatPosition),
                                        builder.constant(index, whatPositionBits)) : null;
                        ExpressionBinaryOperator me = index >= cast.portGroupResolved.dataFieldByteCount ?
                                builder.moreOrEqual(getWhatPosition(cast, builder, readWhatPosition),
                                        builder.constant(index - cast.portGroupResolved.dataFieldByteCount + 1,
                                                whatPositionBits)) : null;
                        Expression currentData = builder.name((StatementVariableDeclaration) cast.what.resolvedEntity);
                        if (cast.kind == StatementMacroRead.Kind.VariableLength) {
                            currentData = builder.part(currentData, "Data");
                        }
                        ExpressionFunction newData = builder.slice(
                                builder.part(builder.name(cast.portGroupResolved.portA), "Data"), builder.concatenate(
                                        builder.sum(
                                                builder.constant(cast.portGroupResolved.dataFieldByteCount - 1 - index,
                                                        whatPositionBits),
                                                getWhatPosition(cast, builder, readWhatPosition)),
                                        builder.constant(0, 3)), builder.constant(8, 4));
                        concatenateParameters[index] = le != null || me != null ?
                                builder.ifte(le != null ? (me != null ? builder.and(le, me) : le) : me, newData,
                                        builder.slice(currentData, builder.constant((whatSizeInBytes - 1 - index) << 3,
                                                whatPositionBits + 3), builder.constant(8, 4))) : newData;
                    }
                    Expression newData = builder.concatenate(concatenateParameters);
                    Expression lastCondition = builder.and(
                            builder.equal(builder.name(cast.portGroupResolved.readBusPosition),
                                    builder.part(builder.name(cast.portGroupResolved.portA), "ByteCount")),
                            builder.part(builder.name(cast.portGroupResolved.portA), "Last"));
                    if (cast.kind == StatementMacroRead.Kind.VariableLength) {
                        TreeMap<String, FieldOrParameter> fields = new TreeMap<>();
                        fields.put("Data", new FieldOrParameter(null, newData));
                        fields.put("ByteCount", new FieldOrParameter(null,
                                builder.sum(getWhatPosition(cast, builder, readWhatPosition),
                                        builder.name(cast.portGroupResolved.readBusPosition))));
                        fields.put("Last", new FieldOrParameter(null, lastCondition));
                        newData = new ExpressionObject(statement.locationRange, cast.what.resolvedTypeReference, fields,
                                transform.idGenerator.generateId());
                    }
                    whileBody.add(builder.expression((StatementVariableDeclaration) cast.what.resolvedEntity, newData,
                            "updateWhat"));
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        whileBody.add(builder.expression(readWhatPosition,
                                builder.sum(getWhatPosition(cast, builder, readWhatPosition),
                                        builder.name(cast.portGroupResolved.readBusPosition)), "advanceWhatPosition"));
                    }
                    LinkedList<Statement> ifCompleteBody = new LinkedList<>();
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        ifCompleteBody.add(
                                builder.expression(castVariable, builder.constant(1, 1), "assignCompleteTrue"));
                    }
                    LinkedList<Statement> ifCompleteAndLastBody = new LinkedList<>();
                    ifCompleteAndLastBody.add(builder.expression(cast.portGroupResolved.readBusPosition,
                            builder.constant(0, cast.portGroupResolved.readBusPosition.typeReference.arraySize),
                            "completeAndLastResetReadBusPosition"));
                    ifCompleteBody.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                            cast.kind == StatementMacroRead.Kind.FixedLength ? builder.name(last) :
                                    builder.part(cast.what, "Last"),
                            new StatementBlock(statement.locationRange, ifCompleteAndLastBody), null,
                            "ifCompleteAndLast", transform.idGenerator.generateId()));
                    ifCompleteBody.add(
                            new StatementLoopControl(statement.locationRange, StatementLoopControl.Kind.Break,
                                    "ifCompleteBreak", transform.idGenerator.generateId()));
                    whileBody.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.If,
                            builder.equal(getWhatPosition(cast, builder, readWhatPosition),
                                    builder.constant(whatSizeInBytes, whatPositionBits)),
                            new StatementBlock(statement.locationRange, ifCompleteBody), null, "ifComplete",
                            transform.idGenerator.generateId()));
                    if (cast.kind == StatementMacroRead.Kind.FixedLength) {
                        whileBody.add(builder.expression(last, lastCondition, "assignLast"));
                    }
                    newStatements.add(new StatementIfWhile(statement.locationRange, StatementIfWhile.Kind.While,
                            builder.constant(1, 1), new StatementBlock(statement.locationRange, whileBody), null,
                            "while", transform.idGenerator.generateId()));
                }
                case null, default -> newStatements.add(statement);
            }
            if (statement instanceof StatementIfWhile cast) {
                replaceMacros(cast.thenBranch, transform, bitType, blankType);
                if (cast.elseBranch != null) {
                    replaceMacros(cast.elseBranch, transform, bitType, blankType);
                }
            }
        }
        block.statements = newStatements;
    }

    private static Expression getWhatPosition(StatementMacroRead cast, MacroBuilder builder,
                                              StatementVariableDeclaration readWhatPosition) {
        return cast.kind == StatementMacroRead.Kind.FixedLength ? builder.name(readWhatPosition) :
                builder.part(builder.name((StatementVariableDeclaration) cast.what.resolvedEntity), "ByteCount");
    }
}
