/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.request.SolrRequestInfo;

/**
 * A Simple Utility class for parsing "math" like strings relating to Dates.
 * <p>
 * <p>
 * The basic syntax support addition, subtraction and rounding at various
 * levels of granularity (or "units").  Commands can be chained together
 * and are parsed from left to right.  '+' and '-' denote addition and
 * subtraction, while '/' denotes "round".  Round requires only a unit, while
 * addition/subtraction require an integer value and a unit.
 * Command strings must not include white space, but the "No-Op" command
 * (empty string) is allowed....
 * </p>
 * <p>
 * <pre>
 *   /HOUR
 *      ... Round to the start of the current hour
 *   /DAY
 *      ... Round to the start of the current day
 *   +2YEARS
 *      ... Exactly two years in the future from now
 *   -1DAY
 *      ... Exactly 1 day prior to now
 *   /DAY+6MONTHS+3DAYS
 *      ... 6 months and 3 days in the future from the start of
 *          the current day
 *   +6MONTHS+3DAYS/DAY
 *      ... 6 months and 3 days in the future from now, rounded
 *          down to nearest day
 * </pre>
 * <p>
 * <p>
 * (Multiple aliases exist for the various units of time (ie:
 * <code>MINUTE</code> and <code>MINUTES</code>; <code>MILLI</code>,
 * <code>MILLIS</code>, <code>MILLISECOND</code>, and
 * <code>MILLISECONDS</code>.)  The complete list can be found by
 * inspecting the keySet of {@link #CALENDAR_UNITS})
 * </p>
 * <p>
 * <p>
 * All commands are relative to a "now" which is fixed in an instance of
 * DateMathParser such that
 * <code>p.parseMath("+0MILLISECOND").equals(p.parseMath("+0MILLISECOND"))</code>
 * no matter how many wall clock milliseconds elapse between the two
 * distinct calls to parse (Assuming no other thread calls
 * "<code>setNow</code>" in the interim).  The default value of 'now' is
 * the time at the moment the <code>DateMathParser</code> instance is
 * constructed, unless overridden by the {@link CommonParams#NOW NOW}
 * request param.
 * </p>
 * <p>
 * <p>
 * All commands are also affected to the rules of a specified {@link TimeZone}
 * (including the start/end of DST if any) which determine when each arbitrary
 * day starts.  This not only impacts rounding/adding of DAYs, but also
 * cascades to rounding of HOUR, MIN, MONTH, YEAR as well.  The default
 * <code>TimeZone</code> used is <code>UTC</code> unless  overridden by the
 * {@link CommonParams#TZ TZ}
 * request param.
 * </p>
 * <p>
 * <p>
 * Historical dates:  The calendar computation is completely done with the
 * Gregorian system/algorithm.  It does <em>not</em> switch to Julian or
 * anything else, unlike the default {@link java.util.GregorianCalendar}.
 * </p>
 *
 * @see SolrRequestInfo#getClientTimeZone
 * @see SolrRequestInfo#getNOW
 */
public class DateMathParser {

    public static final TimeZone Shanghai = TimeZone.getTimeZone("Asia/Shanghai");

    /**
     * Default TimeZone for DateMath rounding (Asia/Shanghai)
     */
    public static final TimeZone DEFAULT_MATH_TZ = Shanghai;

    /**
     * Differs by {@link DateTimeFormatter#ISO_INSTANT} in that it's lenient.
     *
     * @see #parseNoMath(String)
     */
    public static final DateTimeFormatter PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().parseLenient().appendInstant().toFormatter(Locale.getDefault());

    /**
     * A mapping from (uppercased) String labels identifying time units,
     * to the corresponding {@link ChronoUnit} enum (e.g. "YEARS") used to
     * set/add/roll that unit of measurement.
     * <p>
     * <p>
     * A single logical unit of time might be represented by multiple labels
     * for convenience (ie: <code>DATE==DAYS</code>,
     * <code>MILLI==MILLIS</code>)
     * </p>
     *
     * @see Calendar
     */
    public static final Map<String, ChronoUnit> CALENDAR_UNITS = makeUnitsMap();

    public static final List<String> supportDateFormat;

    static {
        supportDateFormat = new ArrayList<String>();
        supportDateFormat.add("yyyy-MM-dd HH:mm:ss.SSS");
        supportDateFormat.add("yyyy-MM-dd HH:m:ss.SSS");
        supportDateFormat.add("yyyy-MM-dd H:mm:ss.SSS");
        supportDateFormat.add("yyyy-MM-dd H:m:ss.SSS");
        supportDateFormat.add("yyyy-MM-dd HH:mm:s.SSS");
        supportDateFormat.add("yyyy-MM-dd HH:m:s.SSS");
        supportDateFormat.add("yyyy-MM-dd H:mm:s.SSS");
        supportDateFormat.add("yyyy-MM-dd H:m:s.SSS");
        supportDateFormat.add("yyyy-MM-dd HH:mm:ss:SSS");
        supportDateFormat.add("yyyy-MM-dd HH:m:ss:SSS");
        supportDateFormat.add("yyyy-MM-dd H:mm:ss:SSS");
        supportDateFormat.add("yyyy-MM-dd H:m:ss:SSS");
        supportDateFormat.add("yyyy-MM-dd HH:mm:s:SSS");
        supportDateFormat.add("yyyy-MM-dd HH:m:s:SSS");
        supportDateFormat.add("yyyy-MM-dd H:mm:s:SSS");
        supportDateFormat.add("yyyy-MM-dd H:m:s:SSS");
        supportDateFormat.add("yyyy-MM-dd HH:mm:ss");
        supportDateFormat.add("yyyy-MM-dd HH:m:ss");
        supportDateFormat.add("yyyy-MM-dd H:mm:ss");
        supportDateFormat.add("yyyy-MM-dd H:m:ss");

        supportDateFormat.add("yyyy/MM/dd HH:mm:ss.SSS");
        supportDateFormat.add("yyyy/MM/dd HH:m:ss.SSS");
        supportDateFormat.add("yyyy/MM/dd H:mm:ss.SSS");
        supportDateFormat.add("yyyy/MM/dd H:m:ss.SSS");
        supportDateFormat.add("yyyy/MM/dd HH:mm:s.SSS");
        supportDateFormat.add("yyyy/MM/dd HH:m:s.SSS");
        supportDateFormat.add("yyyy/MM/dd H:mm:s.SSS");
        supportDateFormat.add("yyyy/MM/dd H:m:s.SSS");
        supportDateFormat.add("yyyy/MM/dd HH:mm:ss:SSS");
        supportDateFormat.add("yyyy/MM/dd HH:m:ss:SSS");
        supportDateFormat.add("yyyy/MM/dd H:mm:ss:SSS");
        supportDateFormat.add("yyyy/MM/dd H:m:ss:SSS");
        supportDateFormat.add("yyyy/MM/dd HH:mm:s:SSS");
        supportDateFormat.add("yyyy/MM/dd HH:m:s:SSS");
        supportDateFormat.add("yyyy/MM/dd H:mm:s:SSS");
        supportDateFormat.add("yyyy/MM/dd H:m:s:SSS");
        supportDateFormat.add("yyyy/MM/dd HH:mm:ss");
        supportDateFormat.add("yyyy/MM/dd HH:m:ss");
        supportDateFormat.add("yyyy/MM/dd H:mm:ss");
        supportDateFormat.add("yyyy/MM/dd H:m:ss");

        supportDateFormat.add("yyyy.MM.dd HH:mm:ss.SSS");
        supportDateFormat.add("yyyy.MM.dd HH:m:ss.SSS");
        supportDateFormat.add("yyyy.MM.dd H:mm:ss.SSS");
        supportDateFormat.add("yyyy.MM.dd H:m:ss.SSS");
        supportDateFormat.add("yyyy.MM.dd HH:mm:s.SSS");
        supportDateFormat.add("yyyy.MM.dd HH:m:s.SSS");
        supportDateFormat.add("yyyy.MM.dd H:mm:s.SSS");
        supportDateFormat.add("yyyy.MM.dd H:m:s.SSS");
        supportDateFormat.add("yyyy.MM.dd HH:mm:ss:SSS");
        supportDateFormat.add("yyyy.MM.dd HH:m:ss:SSS");
        supportDateFormat.add("yyyy.MM.dd H:mm:ss:SSS");
        supportDateFormat.add("yyyy.MM.dd H:m:ss:SSS");
        supportDateFormat.add("yyyy.MM.dd HH:mm:s:SSS");
        supportDateFormat.add("yyyy.MM.dd HH:m:s:SSS");
        supportDateFormat.add("yyyy.MM.dd H:mm:s:SSS");
        supportDateFormat.add("yyyy.MM.dd H:m:s:SSS");
        supportDateFormat.add("yyyy.MM.dd HH:mm:ss");
        supportDateFormat.add("yyyy.MM.dd HH:m:ss");
        supportDateFormat.add("yyyy.MM.dd H:mm:ss");
        supportDateFormat.add("yyyy.MM.dd H:m:ss");

        supportDateFormat.add("MM-dd-yyyy HH:mm:ss.SSS");
        supportDateFormat.add("MM-dd-yyyy HH:m:ss.SSS");
        supportDateFormat.add("MM-dd-yyyy H:mm:ss.SSS");
        supportDateFormat.add("MM-dd-yyyy H:m:ss.SSS");
        supportDateFormat.add("MM-dd-yyyy HH:mm:s.SSS");
        supportDateFormat.add("MM-dd-yyyy HH:m:s.SSS");
        supportDateFormat.add("MM-dd-yyyy H:mm:s.SSS");
        supportDateFormat.add("MM-dd-yyyy H:m:s.SSS");
        supportDateFormat.add("MM-dd-yyyy HH:mm:ss:SSS");
        supportDateFormat.add("MM-dd-yyyy HH:m:ss:SSS");
        supportDateFormat.add("MM-dd-yyyy H:mm:ss:SSS");
        supportDateFormat.add("MM-dd-yyyy H:m:ss:SSS");
        supportDateFormat.add("MM-dd-yyyy HH:mm:s:SSS");
        supportDateFormat.add("MM-dd-yyyy HH:m:s:SSS");
        supportDateFormat.add("MM-dd-yyyy H:mm:s:SSS");
        supportDateFormat.add("MM-dd-yyyy H:m:s:SSS");
        supportDateFormat.add("MM-dd-yyyy HH:mm:ss");
        supportDateFormat.add("MM-dd-yyyy HH:m:ss");
        supportDateFormat.add("MM-dd-yyyy H:mm:ss");
        supportDateFormat.add("MM-dd-yyyy H:m:ss");

        supportDateFormat.add("MM/dd/yyyy HH:mm:ss.SSS");
        supportDateFormat.add("MM/dd/yyyy HH:m:ss.SSS");
        supportDateFormat.add("MM/dd/yyyy H:mm:ss.SSS");
        supportDateFormat.add("MM/dd/yyyy H:m:ss.SSS");
        supportDateFormat.add("MM/dd/yyyy HH:mm:s.SSS");
        supportDateFormat.add("MM/dd/yyyy HH:m:s.SSS");
        supportDateFormat.add("MM/dd/yyyy H:mm:s.SSS");
        supportDateFormat.add("MM/dd/yyyy H:m:s.SSS");
        supportDateFormat.add("MM/dd/yyyy HH:mm:ss:SSS");
        supportDateFormat.add("MM/dd/yyyy HH:m:ss:SSS");
        supportDateFormat.add("MM/dd/yyyy H:mm:ss:SSS");
        supportDateFormat.add("MM/dd/yyyy H:m:ss:SSS");
        supportDateFormat.add("MM/dd/yyyy HH:mm:s:SSS");
        supportDateFormat.add("MM/dd/yyyy HH:m:s:SSS");
        supportDateFormat.add("MM/dd/yyyy H:mm:s:SSS");
        supportDateFormat.add("MM/dd/yyyy H:m:s:SSS");
        supportDateFormat.add("MM/dd/yyyy HH:mm:ss");
        supportDateFormat.add("MM/dd/yyyy HH:m:ss");
        supportDateFormat.add("MM/dd/yyyy H:mm:ss");
        supportDateFormat.add("MM/dd/yyyy H:m:ss");

        supportDateFormat.add("MM.dd.yyyy HH:mm:ss.SSS");
        supportDateFormat.add("MM.dd.yyyy HH:m:ss.SSS");
        supportDateFormat.add("MM.dd.yyyy H:mm:ss.SSS");
        supportDateFormat.add("MM.dd.yyyy H:m:ss.SSS");
        supportDateFormat.add("MM.dd.yyyy HH:mm:s.SSS");
        supportDateFormat.add("MM.dd.yyyy HH:m:s.SSS");
        supportDateFormat.add("MM.dd.yyyy H:mm:s.SSS");
        supportDateFormat.add("MM.dd.yyyy H:m:s.SSS");
        supportDateFormat.add("MM.dd.yyyy HH:mm:ss:SSS");
        supportDateFormat.add("MM.dd.yyyy HH:m:ss:SSS");
        supportDateFormat.add("MM.dd.yyyy H:mm:ss:SSS");
        supportDateFormat.add("MM.dd.yyyy H:m:ss:SSS");
        supportDateFormat.add("MM.dd.yyyy HH:mm:s:SSS");
        supportDateFormat.add("MM.dd.yyyy HH:m:s:SSS");
        supportDateFormat.add("MM.dd.yyyy H:mm:s:SSS");
        supportDateFormat.add("MM.dd.yyyy H:m:s:SSS");
        supportDateFormat.add("MM.dd.yyyy HH:mm:ss");
        supportDateFormat.add("MM.dd.yyyy HH:m:ss");
        supportDateFormat.add("MM.dd.yyyy H:mm:ss");
        supportDateFormat.add("MM.dd.yyyy H:m:ss");

        //begin
        supportDateFormat.add("yyyy-MM-dd HH:mm");
        supportDateFormat.add("yyyy-MM-dd HH:m");
        supportDateFormat.add("yyyy-MM-dd H:mm");
        supportDateFormat.add("yyyy-MM-dd H:m");

        supportDateFormat.add("yyyy/MM/dd HH:mm");
        supportDateFormat.add("yyyy/MM/dd HH:m");
        supportDateFormat.add("yyyy/MM/dd H:mm");
        supportDateFormat.add("yyyy/MM/dd H:m");

        supportDateFormat.add("yyyy.MM.dd HH:mm");
        supportDateFormat.add("yyyy.MM.dd HH:m");
        supportDateFormat.add("yyyy.MM.dd H:mm");
        supportDateFormat.add("yyyy.MM.dd H:m");

        supportDateFormat.add("MM-dd-yyyy HH:mm");
        supportDateFormat.add("MM-dd-yyyy HH:m");
        supportDateFormat.add("MM-dd-yyyy H:mm");
        supportDateFormat.add("MM-dd-yyyy H:m");

        supportDateFormat.add("MM/dd/yyyy HH:mm");
        supportDateFormat.add("MM/dd/yyyy HH:m");
        supportDateFormat.add("MM/dd/yyyy H:mm");
        supportDateFormat.add("MM/dd/yyyy H:m");

        supportDateFormat.add("MM.dd.yyyy HH:mm");
        supportDateFormat.add("MM.dd.yyyy HH:m");
        supportDateFormat.add("MM.dd.yyyy H:mm");
        supportDateFormat.add("MM.dd.yyyy H:m");

        supportDateFormat.add("yyyy-MM-dd hh:mm");
        supportDateFormat.add("yyyy-MM-dd hh:m");
        supportDateFormat.add("yyyy-MM-dd h:mm");
        supportDateFormat.add("yyyy-MM-dd h:m");

        supportDateFormat.add("yyyy/MM/dd hh:mm");
        supportDateFormat.add("yyyy/MM/dd hh:m");
        supportDateFormat.add("yyyy/MM/dd h:mm");
        supportDateFormat.add("yyyy/MM/dd h:m");

        supportDateFormat.add("yyyy.MM.dd hh:mm");
        supportDateFormat.add("yyyy.MM.dd hh:m");
        supportDateFormat.add("yyyy.MM.dd h:mm");
        supportDateFormat.add("yyyy.MM.dd h:m");

        supportDateFormat.add("MM-dd-yyyy hh:mm");
        supportDateFormat.add("MM-dd-yyyy hh:m");
        supportDateFormat.add("MM-dd-yyyy h:mm");
        supportDateFormat.add("MM-dd-yyyy h:m");

        supportDateFormat.add("MM/dd/yyyy hh:mm");
        supportDateFormat.add("MM/dd/yyyy hh:m");
        supportDateFormat.add("MM/dd/yyyy h:mm");
        supportDateFormat.add("MM/dd/yyyy h:m");

        supportDateFormat.add("MM.dd.yyyy hh:mm");
        supportDateFormat.add("MM.dd.yyyy hh:m");
        supportDateFormat.add("MM.dd.yyyy h:mm");
        supportDateFormat.add("MM.dd.yyyy h:m");
        //
        supportDateFormat.add("yyyy-MM-dd");
        supportDateFormat.add("yyyy/MM/dd");
        supportDateFormat.add("yyyy.MM.dd");
        supportDateFormat.add("MM-dd-yyyy");
        supportDateFormat.add("MM/dd/yyyy");
        supportDateFormat.add("MM.dd.yyyy");

        supportDateFormat.add("yyyy-MM");
        supportDateFormat.add("yyyy/MM");
        supportDateFormat.add("yyyy.MM");
        supportDateFormat.add("MM-yyyy");
        supportDateFormat.add("MM/yyyy");
        supportDateFormat.add("MM.yyyy");

        supportDateFormat.add("yyyy");
        supportDateFormat.add("yyyy");
        supportDateFormat.add("yyyy");
        supportDateFormat.add("yyyy");
        supportDateFormat.add("yyyy");
        supportDateFormat.add("yyyy");
    }

    /**
     * @see #CALENDAR_UNITS
     */
    private static Map<String, ChronoUnit> makeUnitsMap() {

        // NOTE: consciously choosing not to support WEEK at this time,
        // because of complexity in rounding down to the nearest week
        // around a month/year boundary.
        // (Not to mention: it's not clear what people would *expect*)
        //
        // If we consider adding some time of "week" support, then
        // we probably need to change "Locale loc" to default to something
        // from a param via SolrRequestInfo as well.

        Map<String, ChronoUnit> units = new HashMap<>(13);
        units.put("YEAR", ChronoUnit.YEARS);
        units.put("YEARS", ChronoUnit.YEARS);
        units.put("MONTH", ChronoUnit.MONTHS);
        units.put("MONTHS", ChronoUnit.MONTHS);
        units.put("DAY", ChronoUnit.DAYS);
        units.put("DAYS", ChronoUnit.DAYS);
        units.put("DATE", ChronoUnit.DAYS);
        units.put("HOUR", ChronoUnit.HOURS);
        units.put("HOURS", ChronoUnit.HOURS);
        units.put("MINUTE", ChronoUnit.MINUTES);
        units.put("MINUTES", ChronoUnit.MINUTES);
        units.put("SECOND", ChronoUnit.SECONDS);
        units.put("SECONDS", ChronoUnit.SECONDS);
        units.put("MILLI", ChronoUnit.MILLIS);
        units.put("MILLIS", ChronoUnit.MILLIS);
        units.put("MILLISECOND", ChronoUnit.MILLIS);
        units.put("MILLISECONDS", ChronoUnit.MILLIS);

        // NOTE: Maybe eventually support NANOS

        return units;
    }

    /**
     * Returns a modified time by "adding" the specified value of units
     *
     * @throws IllegalArgumentException if unit isn't recognized.
     * @see #CALENDAR_UNITS
     */
    private static LocalDateTime add(LocalDateTime t, int val, String unit) {
        ChronoUnit uu = CALENDAR_UNITS.get(unit);
        if (null == uu) {
            throw new IllegalArgumentException("Adding Unit not recognized: "
                    + unit);
        }
        return t.plus(val, uu);
    }

    /**
     * Returns a modified time by "rounding" down to the specified unit
     *
     * @throws IllegalArgumentException if unit isn't recognized.
     * @see #CALENDAR_UNITS
     */
    private static LocalDateTime round(LocalDateTime t, String unit) {
        ChronoUnit uu = CALENDAR_UNITS.get(unit);
        if (null == uu) {
            throw new IllegalArgumentException("Rounding Unit not recognized: "
                    + unit);
        }
        // note: OffsetDateTime.truncatedTo does not support >= DAYS units so we handle those
        switch (uu) {
            case YEARS:
                return LocalDateTime.of(LocalDate.of(t.getYear(), 1, 1), LocalTime.MIDNIGHT); // midnight is 00:00:00
            case MONTHS:
                return LocalDateTime.of(LocalDate.of(t.getYear(), t.getMonth(), 1), LocalTime.MIDNIGHT);
            case DAYS:
                return LocalDateTime.of(t.toLocalDate(), LocalTime.MIDNIGHT);
            default:
                assert !uu.isDateBased();// >= DAY
                return t.truncatedTo(uu);
        }
    }

    /**
     * Parses a String which may be a date (in the standard ISO-8601 format)
     * followed by an optional math expression.
     *
     * @param now an optional fixed date to use as "NOW"
     * @param val the string to parse
     */
    public static Date parseMath(Date now, String val) {
        String math = null;
        final DateMathParser p = new DateMathParser();
        if (null != now) {
            p.setNow(now);
        }
        if (val.startsWith("NOW")) {
            math = val.substring("NOW".length());
        } else {
            final int zz = val.indexOf('Z');
            if (zz == -1) {
                TimeZone tz = getTZ(null);
                if(!tz.getID().equals("Asia/Shanghai") && !!tz.getID().equals("GMT+8")) {
                    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                            "Invalid Date String:'" + val + '\'');
                } else {
                    try {
                        p.setNow(parseChineseNoMatch(val));
                    } catch (DateTimeParseException e) {
                        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                                "Invalid Date in Date Math String:'" + val + '\'', e);
                    }
                }
            } else {
                math = val.substring(zz + 1);
                try {
                    p.setNow(parseNoMath(val.substring(0, zz + 1)));
                } catch (DateTimeParseException e) {
                    throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                            "Invalid Date in Date Math String:'" + val + '\'', e);
                }
            }
        }
        if (null == math || math.equals("")) {
            return p.getNow();
        }

        try {
            return p.parseMath(math);
        } catch (ParseException e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "Invalid Date Math String:'" + val + '\'', e);
        }
    }

    /**
     * Parsing Solr dates <b>without DateMath</b>.
     * This is the standard/pervasive ISO-8601 UTC format but is configured with some leniency.
     * <p>
     * Callers should almost always call {@link #parseMath(Date, String)} instead.
     *
     * @throws DateTimeParseException if it can't parse
     */
    private static Date parseNoMath(String val) {
        //TODO write the equivalent of a Date::from; avoids Instant -> Date
        return new Date(PARSER.parse(val, Instant::from).toEpochMilli());
    }

    protected static Date parseChineseNoMatch(String val) {
        for(String format : supportDateFormat) {
            Date date = DateUtils.string2TimezoneDate(format,val,getTZ(null).getID());
            if(null == date) {
                continue;
            }
            return date;
        }
        return null;
    }

    /*public static void main(String[] args) {
         Date date = parseChineseNoMatch("2016/11/24");
        String dateStr = DateUtils.parseDate(date,"yyyy-MM-dd HH:mm:ss:SSS");
        System.out.println(dateStr);
    }*/

    private TimeZone zone;
    private Locale loc;
    private Date now;

    /**
     * Default constructor that assumes UTC should be used for rounding unless
     * otherwise specified in the SolrRequestInfo
     *
     * @see SolrRequestInfo#getClientTimeZone
     */
    public DateMathParser() {
        this(null);
    }

    /**
     * @param tz The TimeZone used for rounding (to determine when hours/days begin).  If null, then this method defaults
     *           to the value dictated by the SolrRequestInfo if it exists -- otherwise it uses UTC.
     * @see #DEFAULT_MATH_TZ
     * @see Calendar#getInstance(TimeZone, Locale)
     * @see SolrRequestInfo#getClientTimeZone
     */
    public DateMathParser(TimeZone tz) {
        zone = getTZ(tz);
    }

    public static TimeZone getTZ(TimeZone tz) {
        if (null == tz) {
            SolrRequestInfo reqInfo = SolrRequestInfo.getRequestInfo();
            tz = (null != reqInfo) ? reqInfo.getClientTimeZone() : DEFAULT_MATH_TZ;
        }
        return (null != tz) ? tz : DEFAULT_MATH_TZ;
    }

    /**
     * @return the time zone
     */
    public TimeZone getTimeZone() {
        return this.zone;
    }

    /**
     * Defines this instance's concept of "now".
     *
     * @see #getNow
     */
    public void setNow(Date n) {
        now = n;
    }

    /**
     * Returns a clone of this instance's concept of "now" (never null).
     * <p>
     * If setNow was never called (or if null was specified) then this method
     * first defines 'now' as the value dictated by the SolrRequestInfo if it
     * exists -- otherwise it uses a new Date instance at the moment getNow()
     * is first called.
     *
     * @see #setNow
     * @see SolrRequestInfo#getNOW
     */
    public Date getNow() {
        if (now == null) {
            SolrRequestInfo reqInfo = SolrRequestInfo.getRequestInfo();
            if (reqInfo == null) {
                // fall back to current time if no request info set
                now = new Date();
            } else {
                now = reqInfo.getNOW(); // never null
            }
        }
        return (Date) now.clone();
    }

    /**
     * Parses a string of commands relative "now" are returns the resulting Date.
     *
     * @throws ParseException positions in ParseExceptions are token positions, not character positions.
     */
    public Date parseMath(String math) throws ParseException {
    /* check for No-Op */
        if (0 == math.length()) {
            return getNow();
        }

        ZoneId zoneId = zone.toZoneId();
        // localDateTime is a date and time local to the timezone specified
        LocalDateTime localDateTime = ZonedDateTime.ofInstant(getNow().toInstant(), zoneId).toLocalDateTime();

        String[] ops = splitter.split(math);
        int pos = 0;
        while (pos < ops.length) {

            if (1 != ops[pos].length()) {
                throw new ParseException
                        ("Multi character command found: \"" + ops[pos] + "\"", pos);
            }
            char command = ops[pos++].charAt(0);

            switch (command) {
                case '/':
                    if (ops.length < pos + 1) {
                        throw new ParseException
                                ("Need a unit after command: \"" + command + "\"", pos);
                    }
                    try {
                        localDateTime = round(localDateTime, ops[pos++]);
                    } catch (IllegalArgumentException e) {
                        throw new ParseException
                                ("Unit not recognized: \"" + ops[pos - 1] + "\"", pos - 1);
                    }
                    break;
                case '+': /* fall through */
                case '-':
                    if (ops.length < pos + 2) {
                        throw new ParseException
                                ("Need a value and unit for command: \"" + command + "\"", pos);
                    }
                    int val = 0;
                    try {
                        val = Integer.valueOf(ops[pos++]);
                    } catch (NumberFormatException e) {
                        throw new ParseException
                                ("Not a Number: \"" + ops[pos - 1] + "\"", pos - 1);
                    }
                    if ('-' == command) {
                        val = 0 - val;
                    }
                    try {
                        String unit = ops[pos++];
                        localDateTime = add(localDateTime, val, unit);
                    } catch (IllegalArgumentException e) {
                        throw new ParseException
                                ("Unit not recognized: \"" + ops[pos - 1] + "\"", pos - 1);
                    }
                    break;
                default:
                    throw new ParseException
                            ("Unrecognized command: \"" + command + "\"", pos - 1);
            }
        }

        return Date.from(ZonedDateTime.of(localDateTime, zoneId).toInstant());
    }

    private static Pattern splitter = Pattern.compile("\\b|(?<=\\d)(?=\\D)");

}

