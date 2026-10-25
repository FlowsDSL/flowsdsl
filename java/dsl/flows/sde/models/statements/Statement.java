// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flows author <flows.dsl@proton.me>

package dsl.flows.sde.models.statements;

import dsl.flows.sde.SourceFilenameAndLocationRange;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.TreeMap;

public abstract class Statement {
    public static final Comparator<Statement> COMPARATOR = Comparator.comparingInt(Statement::getId);

    public final SourceFilenameAndLocationRange locationRange;
    public final int id;
    public final String suffixForVariables;
    public LinkedList<StatementNonbranching> unconditionalPredecessors;
    public LinkedList<StatementIfWhile> thenPredecessors;
    public LinkedList<StatementIfWhile> elsePredecessors;
    public TreeMap<StatementStep, StatementRegionAttributes> regionAttributes = new TreeMap<>(Statement.COMPARATOR);

    protected Statement(SourceFilenameAndLocationRange locationRange, String syntheticVariableSuffix, int id) {
        this.locationRange = locationRange;
        this.id = id;
        suffixForVariables = locationRange.start.getNameSuffixForVariables() +
                (syntheticVariableSuffix != null ? syntheticVariableSuffix : "");
    }

    public int getId() {
        return id;
    }
}
