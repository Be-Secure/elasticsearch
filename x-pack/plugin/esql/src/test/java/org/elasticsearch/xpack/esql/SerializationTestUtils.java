/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.RegexpQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;
import org.elasticsearch.test.EqualsHashCodeTestUtils;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.Expression;
import org.elasticsearch.xpack.esql.core.expression.NamedExpression;
import org.elasticsearch.xpack.esql.core.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.esql.expression.function.UnsupportedAttribute;
import org.elasticsearch.xpack.esql.expression.function.aggregate.AggregateFunction;
import org.elasticsearch.xpack.esql.expression.function.scalar.EsqlScalarFunction;
import org.elasticsearch.xpack.esql.io.stream.PlanNameRegistry;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamInput;
import org.elasticsearch.xpack.esql.io.stream.PlanStreamOutput;
import org.elasticsearch.xpack.esql.plan.physical.PhysicalPlan;
import org.elasticsearch.xpack.esql.querydsl.query.SingleValueQuery;
import org.elasticsearch.xpack.esql.session.EsqlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SerializationTestUtils {

    private static final PlanNameRegistry planNameRegistry = new PlanNameRegistry();

    public static void assertSerialization(PhysicalPlan plan) {
        assertSerialization(plan, EsqlTestUtils.TEST_CFG);
    }

    public static void assertSerialization(PhysicalPlan plan, EsqlConfiguration configuration) {
        var deserPlan = serializeDeserialize(
            plan,
            PlanStreamOutput::writePhysicalPlanNode,
            PlanStreamInput::readPhysicalPlanNode,
            configuration
        );
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(plan, unused -> deserPlan);
    }

    public static void assertSerialization(LogicalPlan plan) {
        var deserPlan = serializeDeserialize(plan, PlanStreamOutput::writeLogicalPlanNode, PlanStreamInput::readLogicalPlanNode);
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(plan, unused -> deserPlan);
    }

    public static void assertSerialization(Expression expression) {
        assertSerialization(expression, EsqlTestUtils.TEST_CFG);
    }

    public static void assertSerialization(Expression expression, EsqlConfiguration configuration) {
        Expression deserExpression = serializeDeserialize(
            expression,
            PlanStreamOutput::writeNamedWriteable,
            in -> in.readNamedWriteable(Expression.class),
            configuration
        );
        EqualsHashCodeTestUtils.checkEqualsAndHashCode(expression, unused -> deserExpression);
    }

    public static <T> T serializeDeserialize(T orig, Serializer<T> serializer, Deserializer<T> deserializer) {
        return serializeDeserialize(orig, serializer, deserializer, EsqlTestUtils.TEST_CFG);
    }

    public static <T> T serializeDeserialize(T orig, Serializer<T> serializer, Deserializer<T> deserializer, EsqlConfiguration config) {
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            PlanStreamOutput planStreamOutput = new PlanStreamOutput(out, planNameRegistry, config);
            serializer.write(planStreamOutput, orig);
            StreamInput in = new NamedWriteableAwareStreamInput(
                ByteBufferStreamInput.wrap(BytesReference.toBytes(out.bytes())),
                writableRegistry()
            );
            PlanStreamInput planStreamInput = new PlanStreamInput(in, planNameRegistry, in.namedWriteableRegistry(), config);
            return deserializer.read(planStreamInput);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public interface Serializer<T> {
        void write(PlanStreamOutput out, T object) throws IOException;
    }

    public interface Deserializer<T> {
        T read(PlanStreamInput in) throws IOException;
    }

    public static NamedWriteableRegistry writableRegistry() {
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, TermQueryBuilder.NAME, TermQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, TermsQueryBuilder.NAME, TermsQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, MatchAllQueryBuilder.NAME, MatchAllQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, RangeQueryBuilder.NAME, RangeQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, BoolQueryBuilder.NAME, BoolQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, WildcardQueryBuilder.NAME, WildcardQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, RegexpQueryBuilder.NAME, RegexpQueryBuilder::new));
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, ExistsQueryBuilder.NAME, ExistsQueryBuilder::new));
        entries.add(SingleValueQuery.ENTRY);
        entries.addAll(Attribute.getNamedWriteables());
        entries.add(UnsupportedAttribute.ENTRY);
        entries.addAll(NamedExpression.getNamedWriteables());
        entries.add(UnsupportedAttribute.NAMED_EXPRESSION_ENTRY);
        entries.addAll(Expression.getNamedWriteables());
        entries.addAll(EsqlScalarFunction.getNamedWriteables());
        entries.addAll(AggregateFunction.getNamedWriteables());
        return new NamedWriteableRegistry(entries);
    }
}
