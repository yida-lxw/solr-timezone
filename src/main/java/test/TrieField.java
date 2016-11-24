package test;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.DocValuesRangeQuery;
import org.apache.lucene.search.LegacyNumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionRangeQuery;
import org.apache.solr.search.QParser;
import org.apache.solr.search.function.ValueSourceRangeFilter;

import java.util.Locale;
import java.util.Map;

/**
 * Created by Lanxiaowei
 * Craated on 2016/11/23 21:38
 */
public class TrieField extends org.apache.solr.schema.TrieField {
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    private static long FLOAT_NEGATIVE_INFINITY_BITS = (long)Float.floatToIntBits(Float.NEGATIVE_INFINITY);
    private static long DOUBLE_NEGATIVE_INFINITY_BITS = Double.doubleToLongBits(Double.NEGATIVE_INFINITY);
    private static long FLOAT_POSITIVE_INFINITY_BITS = (long)Float.floatToIntBits(Float.POSITIVE_INFINITY);
    private static long DOUBLE_POSITIVE_INFINITY_BITS = Double.doubleToLongBits(Double.POSITIVE_INFINITY);
    private static long FLOAT_MINUS_ZERO_BITS = (long)Float.floatToIntBits(-0f);
    private static long DOUBLE_MINUS_ZERO_BITS = Double.doubleToLongBits(-0d);
    private static long FLOAT_ZERO_BITS = (long)Float.floatToIntBits(0f);
    private static long DOUBLE_ZERO_BITS = Double.doubleToLongBits(0d);

    /**
     * 时区
     */
    protected String timezone;

    @Override
    protected void init(IndexSchema schema, Map<String, String> args) {
        String tz = args.remove("tz");
        if (tz != null) {
            timezone = tz;
        } else {
            timezone = DEFAULT_TIME_ZONE;
        }
        String t = args.remove("type");
        if (t != null) {
            try {
                type = TrieTypes.valueOf(t.toUpperCase(Locale.getDefault()));
            } catch (IllegalArgumentException e) {
                throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                        "Invalid type specified in schema.xml for field: " + args.get("name"), e);
            }
        }
    }

    @Override
    public Query getRangeQuery(QParser parser, SchemaField field, String min, String max, boolean minInclusive, boolean maxInclusive) {
        if (field.multiValued() && field.hasDocValues() && !field.indexed()) {
            // for the multi-valued dv-case, the default rangeimpl over toInternal is correct
            return super.getRangeQuery(parser, field, min, max, minInclusive, maxInclusive);
        }
        int ps = precisionStep;
        Query query;
        final boolean matchOnly = field.hasDocValues() && !field.indexed();
        switch (type) {
            case INTEGER:
                if (matchOnly) {
                    query = DocValuesRangeQuery.newLongRange(field.getName(),
                            min == null ? null : (long) Integer.parseInt(min),
                            max == null ? null : (long) Integer.parseInt(max),
                            minInclusive, maxInclusive);
                } else {
                    query = LegacyNumericRangeQuery.newIntRange(field.getName(), ps,
                            min == null ? null : Integer.parseInt(min),
                            max == null ? null : Integer.parseInt(max),
                            minInclusive, maxInclusive);
                }
                break;
            case FLOAT:
                if (matchOnly) {
                    return getRangeQueryForFloatDoubleDocValues(field, min, max, minInclusive, maxInclusive);
                } else {
                    query = LegacyNumericRangeQuery.newFloatRange(field.getName(), ps,
                            min == null ? null : Float.parseFloat(min),
                            max == null ? null : Float.parseFloat(max),
                            minInclusive, maxInclusive);
                }
                break;
            case LONG:
                if (matchOnly) {
                    query = DocValuesRangeQuery.newLongRange(field.getName(),
                            min == null ? null : Long.parseLong(min),
                            max == null ? null : Long.parseLong(max),
                            minInclusive, maxInclusive);
                } else {
                    query = LegacyNumericRangeQuery.newLongRange(field.getName(), ps,
                            min == null ? null : Long.parseLong(min),
                            max == null ? null : Long.parseLong(max),
                            minInclusive, maxInclusive);
                }
                break;
            case DOUBLE:
                if (matchOnly) {
                    return getRangeQueryForFloatDoubleDocValues(field, min, max, minInclusive, maxInclusive);
                } else {
                    query = LegacyNumericRangeQuery.newDoubleRange(field.getName(), ps,
                            min == null ? null : Double.parseDouble(min),
                            max == null ? null : Double.parseDouble(max),
                            minInclusive, maxInclusive);
                }
                break;
            case DATE:
                if (matchOnly) {
                    query = DocValuesRangeQuery.newLongRange(field.getName(),
                            min == null ? null : DateMathParser.parseMath(null, min).getTime(),
                            max == null ? null : DateMathParser.parseMath(null, max).getTime(),
                            minInclusive, maxInclusive);
                } else {
                    query = LegacyNumericRangeQuery.newLongRange(field.getName(), ps,
                            min == null ? null : DateMathParser.parseMath(null, min).getTime(),
                            max == null ? null : DateMathParser.parseMath(null, max).getTime(),
                            minInclusive, maxInclusive);
                }
                break;
            default:
                throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "Unknown type for trie field");
        }
        return query;
    }

    private Query getRangeQueryForFloatDoubleDocValues(SchemaField sf, String min, String max, boolean minInclusive, boolean maxInclusive) {
        Query query;
        String fieldName = sf.getName();

        Number minVal = min == null ? null : type == TrieTypes.FLOAT ? Float.parseFloat(min): Double.parseDouble(min);
        Number maxVal = max == null ? null : type == TrieTypes.FLOAT ? Float.parseFloat(max): Double.parseDouble(max);

        Long minBits =
                min == null ? null : type == TrieTypes.FLOAT ? (long) Float.floatToIntBits(minVal.floatValue()): Double.doubleToLongBits(minVal.doubleValue());
        Long maxBits =
                max == null ? null : type == TrieTypes.FLOAT ? (long) Float.floatToIntBits(maxVal.floatValue()): Double.doubleToLongBits(maxVal.doubleValue());

        long negativeInfinityBits = type == TrieTypes.FLOAT ? FLOAT_NEGATIVE_INFINITY_BITS : DOUBLE_NEGATIVE_INFINITY_BITS;
        long positiveInfinityBits = type == TrieTypes.FLOAT ? FLOAT_POSITIVE_INFINITY_BITS : DOUBLE_POSITIVE_INFINITY_BITS;
        long minusZeroBits = type == TrieTypes.FLOAT ? FLOAT_MINUS_ZERO_BITS : DOUBLE_MINUS_ZERO_BITS;
        long zeroBits = type == TrieTypes.FLOAT ? FLOAT_ZERO_BITS : DOUBLE_ZERO_BITS;

        // If min is negative (or -0d) and max is positive (or +0d), then issue a FunctionRangeQuery
        if ((minVal == null || minVal.doubleValue() < 0d || minBits == minusZeroBits) &&
                (maxVal == null || (maxVal.doubleValue() > 0d || maxBits == zeroBits))) {

            ValueSource vs = getValueSource(sf, null);
            query = new FunctionRangeQuery(new ValueSourceRangeFilter(vs, min, max, minInclusive, maxInclusive));

        } else { // If both max and min are negative (or -0d), then issue range query with max and min reversed
            if ((minVal == null || minVal.doubleValue() < 0d || minBits == minusZeroBits) &&
                    (maxVal != null && (maxVal.doubleValue() < 0d || maxBits == minusZeroBits))) {
                query = DocValuesRangeQuery.newLongRange
                        (fieldName, maxBits, (min == null ? negativeInfinityBits : minBits), maxInclusive, minInclusive);
            } else { // If both max and min are positive, then issue range query
                query = DocValuesRangeQuery.newLongRange
                        (fieldName, minBits, (max == null ? positiveInfinityBits : maxBits), minInclusive, maxInclusive);
            }
        }
        return query;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
