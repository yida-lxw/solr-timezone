package test;

import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.DateValueFieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TrieField;
import org.apache.solr.search.QParser;
import org.apache.solr.util.DateMathParser;

import java.text.*;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TrieCNDateField extends TrieField implements DateValueFieldType {
	{
		type = TrieTypes.DATE;
	}

	public static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");

	protected static final TimeZone CANONICAL_TZ = SHANGHAI;

	protected static final Locale CANONICAL_LOCALE = Locale.CHINESE;

	protected static final String NOW = "NOW";

	/**
	 * Parses a String which may be a date (in the standard format) followed by
	 * an optional math expression.
	 * 
	 * @param now
	 *            an optional fixed date to use as "NOW" in the DateMathParser
	 * @param val
	 *            the string to parse
	 */
	public Date parseMath(Date now, String val) {
		String math = null;
		final DateMathParser p = new DateMathParser();

		if (null != now) {
			p.setNow(now);
		}

		if (val.startsWith(NOW)) {
			math = val.substring(NOW.length());
		} else {
			try {
				p.setNow(parseDate(val));
			} catch (ParseException e) {
				throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
						"Invalid Date String:'" + val + '\'');
			}
		}

		if (null == math || math.equals("")) {
			return p.getNow();
		}
		
		try {
			return parseDate(math);
		} catch (ParseException e) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
					"Invalid Date Math String:'" + val + '\'', e);
		}
	}

	/**
	 * Thread safe method that can be used by subclasses to format a Date
	 * without the trailing 'Z'.
	 */
	protected String formatDate(Date d) {
		return fmtThreadLocal.get().format(d);
	}

	/**
	 * Return the standard human readable form of the date
	 */
	public static String formatExternal(Date d) {
		return fmtThreadLocal.get().format(d);
	}

	/**
	 * @see #formatExternal
	 */
	public String toExternal(Date d) {
		return formatExternal(d);
	}

	/**
	 * Thread safe method that can be used by subclasses to parse a Date without
	 * the trailing 'Z'
	 */
	public static Date parseDate(String s) throws ParseException {
		return fmtThreadLocal.get().parse(s);
	}

	@Override
	public Date toObject(IndexableField f) {
		return (Date) super.toObject(f);
	}

	/** TrieDateField specific range query */
	public Query getRangeQuery(QParser parser, SchemaField sf, Date min, Date max) {
		return LongPoint.newRangeQuery(sf.getName(), min.getTime(), max.getTime());
	}

	@Override
	public Object toNativeType(Object val) {
		if (val == null)
			return null;
		if (val instanceof Date) {
			return val;
		}

		if (val instanceof String) {
			return parseMath(null,(String)val);
		}

		return super.toNativeType(val);
	}

	private final static ThreadLocalDateFormat fmtThreadLocal = new ThreadLocalDateFormat(
			new ChineseDateFormat());

	@SuppressWarnings("serial")
	private static class ChineseDateFormat extends SimpleDateFormat {

		protected NumberFormat millisParser = NumberFormat
				.getIntegerInstance(CANONICAL_LOCALE);

		protected NumberFormat millisFormat = new DecimalFormat(".###",
				new DecimalFormatSymbols(CANONICAL_LOCALE));

		public ChineseDateFormat() {
			super("yyyy-MM-dd HH:mm:ss", CANONICAL_LOCALE);
			this.setTimeZone(CANONICAL_TZ);
		}

		@Override
		public Date parse(String i, ParsePosition p) {
			/* delegate to SimpleDateFormat for easy stuff */
			Date d = super.parse(i, p);
			int milliIndex = p.getIndex();
			/* worry about the milliseconds ourselves */
			if (null != d && -1 == p.getErrorIndex()
					&& milliIndex + 1 < i.length()
					&& '.' == i.charAt(milliIndex)) {
				p.setIndex(++milliIndex); // NOTE: ++ to chomp '.'
				Number millis = millisParser.parse(i, p);
				if (-1 == p.getErrorIndex()) {
					int endIndex = p.getIndex();
					d = new Date(d.getTime()
							+ (long) (millis.doubleValue() * Math.pow(10,
									(3 - endIndex + milliIndex))));
				}
			}
			return d;
		}

		@Override
		public StringBuffer format(Date d, StringBuffer toAppendTo,
                                   FieldPosition pos) {
			/* delegate to SimpleDateFormat for easy stuff */
			super.format(d, toAppendTo, pos);
			/* worry about the milliseconds ourselves */
			long millis = d.getTime() % 1000l;
			if (0L == millis) {
				return toAppendTo;
			}
			if (millis < 0L) {
				// original date was prior to epoch
				millis += 1000L;
			}
			int posBegin = toAppendTo.length();
			toAppendTo.append(millisFormat.format(millis / 1000d));
			if (DateFormat.MILLISECOND_FIELD == pos.getField()) {
				pos.setBeginIndex(posBegin);
				pos.setEndIndex(toAppendTo.length());
			}
			return toAppendTo;
		}

		@Override
		public DateFormat clone() {
			ChineseDateFormat c = (ChineseDateFormat) super.clone();
			c.millisParser = NumberFormat.getIntegerInstance(CANONICAL_LOCALE);
			c.millisFormat = new DecimalFormat(".###", new DecimalFormatSymbols(CANONICAL_LOCALE));
			return c;
		}
	}

	private static class ThreadLocalDateFormat extends ThreadLocal<DateFormat> {
		DateFormat proto;

		public ThreadLocalDateFormat(DateFormat d) {
			super();
			proto = d;
		}

		@Override
		protected DateFormat initialValue() {
			return (DateFormat) proto.clone();
		}
	}
	
	public static void main(String[] args) throws ParseException {
		  String dateString = "2015-05-12 16:38:36";
		  TrieCNDateField trieCNDateField = new TrieCNDateField();
		  Date date = trieCNDateField.parseMath(new Date(),dateString);
		  DateFormat formate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		  String s = formate.format(date);
		  System.out.println(s);
	  }
}
