/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ast.tree;

import com.google.common.collect.ImmutableList;
import org.opensearch.sql.ast.AbstractNodeVisitor;
import org.opensearch.sql.ast.Node;
import org.opensearch.sql.ast.expression.Alias;
import org.opensearch.sql.ast.expression.Field;
import org.opensearch.sql.ast.expression.QualifiedName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** AST node represent Lookup operation. */
public class Lookup extends UnresolvedPlan {
    private UnresolvedPlan child;
    private final UnresolvedPlan lookupRelation;
    private final Map<Alias, Field> lookupMappingMap;
    private final OutputStrategy outputStrategy;
    private final Map<Alias, Field> outputCandidateMap;

    public Lookup(
        UnresolvedPlan lookupRelation,
        Map<Alias, Field> lookupMappingMap,
        OutputStrategy outputStrategy,
        Map<Alias, Field> outputCandidateMap) {
        this.lookupRelation = lookupRelation;
        this.lookupMappingMap = lookupMappingMap;
        this.outputStrategy = outputStrategy;
        this.outputCandidateMap = outputCandidateMap;
    }

    @Override
    public UnresolvedPlan attach(UnresolvedPlan child) {
        this.child = new SubqueryAlias(child, "_s"); // add a auto generated alias name
        return this;
    }

    @Override
    public List<? extends Node> getChild() {
        return ImmutableList.of(child);
    }

    @Override
    public <T, C> T accept(AbstractNodeVisitor<T, C> visitor, C context) {
        return visitor.visitLookup(this, context);
    }

    public enum OutputStrategy {
        APPEND,
        REPLACE
    }

    public UnresolvedPlan getLookupRelation() {
        return lookupRelation;
    }

    public String getLookupSubqueryAliasName() {
        return ((SubqueryAlias) lookupRelation).getAlias();
    }

    public String getSourceSubqueryAliasName() {
        return ((SubqueryAlias) child).getAlias();
    }

    /**
     * Lookup mapping field map. For example:
     *  1. When mapping is "name AS cName",
     *     the original key will be Alias(cName, Field(name)), the original value will be Field(cName).
     *     Returns a map which left join key is Field(name), right join key is Field(cName)
     *  2. When mapping is "dept",
     *     the original key is Alias(dept, Field(dept)), the original value is Field(dept).
     *     Returns a map which left join key is Field(dept), the right join key is Field(dept) too.
     */
    public Map<Field, Field> getLookupMappingMap() {
        return lookupMappingMap.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> (Field) (entry.getKey()).getDelegated(),
                Map.Entry::getValue,
                (k, y) -> y,
                LinkedHashMap::new));
    }

    public OutputStrategy getOutputStrategy() {
        return outputStrategy;
    }

    /**
     * Output candidate field map. For example:
     *  1. When output candidate is "name AS cName", the key will be Alias(cName, Field(name)), the value will be Field(cName)
     *  2. When output candidate is "dept", the key is Alias(dept, Field(dept)), value is Field(dept)
     */
    public Map<Alias, Field> getOutputCandidateMap() {
        return outputCandidateMap;
    }

    public List<Field> getSourceOutputFieldListWithSubqueryAlias() {
        return outputCandidateMap.values().stream().map(f ->
            new Field(QualifiedName.of(getSourceSubqueryAliasName(), f.getField().toString()), f.getFieldArgs()))
            .collect(Collectors.toList());
    }

    /** Return the lookup output Field list instead of Alias list */
    public List<Field> getLookupOutputFieldList() {
        return outputCandidateMap.keySet().stream().map(alias -> (Field) alias.getDelegated()).collect(Collectors.toList());
    }

    public boolean allFieldsShouldAppliedToOutputList() {
        return getOutputCandidateMap().isEmpty();
    }

}