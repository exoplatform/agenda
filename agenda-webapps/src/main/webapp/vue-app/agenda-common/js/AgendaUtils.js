import TIMEZONE_IDS from '../json/timezones.json';

export const USER_TIMEZONE_ID = new window.Intl.DateTimeFormat().resolvedOptions().timeZone;
export const TIMEZONES = [];
export const MINIMUM_TIME_INTERVAL = 30;
export const MINIMUM_TIME_INTERVAL_MS = MINIMUM_TIME_INTERVAL * 60 * 1000;
// Same palette as the space settings' calendar color picker (AgendaSpaceAdministration.vue)
export const EVENT_COLOR_SWATCHES = [
  ['#FF0000', '#319ab3', '#f97575'],
  ['#98cc81', '#4273c8', '#cea6ac'],
  ['#bc99e7', '#9ee4f5', '#774ea9'],
  ['#ffa500', '#bed67e', '#0E100F'],
  ['#ffaacc', '#0000AA', '#000055'],
];

export function initEventForm(agendaEvent, deleteDates) {
  if (!agendaEvent.timeZoneId) {
    agendaEvent.timeZoneId = USER_TIMEZONE_ID;
  }
  if (!agendaEvent.calendar) {
    agendaEvent.calendar = {};
  }
  if (!agendaEvent.calendar.owner) {
    agendaEvent.calendar.owner = {};
  }
  if (!agendaEvent.reminders) {
    agendaEvent.reminders = [];
  }
  if (!agendaEvent.attachments) {
    agendaEvent.attachments = [];
  }
  if (!agendaEvent.attendees) {
    agendaEvent.attendees = [];
  }
  if (!agendaEvent.dateOptions) {
    agendaEvent.dateOptions = [];
  }

  if (!agendaEvent.startDate && agendaEvent.start) {
    agendaEvent.startDate = agendaEvent.start && toDate(agendaEvent.start) || new Date();
    agendaEvent.startDate = roundTime(new Date(agendaEvent.startDate).getTime());
  }
  if (!agendaEvent.endDate && agendaEvent.end) {
    agendaEvent.endDate = toDate(agendaEvent.end).getTime();
  }

  if (agendaEvent.status === 'TENTATIVE') {
    agendaEvent.dateOptions.forEach(dateOption => {
      if (!dateOption.startDate) {
        dateOption.startDate = dateOption.start && toDate(dateOption.start) || new Date();
        dateOption.startDate = roundTime(new Date(dateOption.startDate).getTime());
      }

      if (!dateOption.endDate) {
        if (dateOption.end) {
          dateOption.endDate = toDate(dateOption.end).getTime();
        } else if (dateOption.start) {
          dateOption.endDate = new Date(dateOption.startDate).getTime() + MINIMUM_TIME_INTERVAL_MS;
        }
      }
    });
  } else if (agendaEvent.startDate && agendaEvent.endDate) {
    agendaEvent.dateOptions = [{
      allDay: agendaEvent.allDay,
      start: agendaEvent.start,
      startDate: agendaEvent.startDate,
      end: agendaEvent.end,
      endDate: agendaEvent.endDate,
    }];
  }

  const eventColor = agendaEvent && (agendaEvent.color || agendaEvent.calendar && agendaEvent.calendar.color) || '#2196F3';
  agendaEvent.dateOptions.forEach(dateOption => {
    dateOption.dateOption = true;
    dateOption.eventId = agendaEvent.id;
    dateOption.summary = agendaEvent.summary;
    dateOption.color = eventColor;
    dateOption.occurrence = agendaEvent.occurrence;
    dateOption.parent = {
      id: agendaEvent.parent && agendaEvent.parent.id,
    };
  });

  if (deleteDates) {
    agendaEvent.start = null;
    agendaEvent.startDate = null;
    agendaEvent.end = null;
    agendaEvent.endDate = null;
    agendaEvent.allDay = false;
  }
}

/*
 * The bounds the event form enforces on the details step. Named because two
 * screens now ask the same question and a bare 1024 in one of them would not
 * read as the same rule as a bare 1024 in the other.
 */
export const EVENT_TITLE_MAX_LENGTH = 1024;
export const EVENT_DESCRIPTION_MAX_LENGTH = 1300;

/**
 * Whether the event's details step is complete: a title within bounds, a
 * destination the server can resolve, and a description within bounds.
 *
 * This is the rule behind the form's own "next step" button. It lives here,
 * and not in AgendaEventForm, because the quick-add drawer now offers a second
 * route into the date step and must admit exactly whom the form admits — two
 * copies of this rule would drift, and the drawer would end up opening a step
 * the form itself refuses to leave.
 *
 * @param {Object} agendaEvent the event being edited
 * @param {Function} htmlToText the portal's HTML-to-text helper, as
 *        $utils.htmlToText — passed in because this module is plain JS and has
 *        no Vue prototype to read it from
 * @returns {Boolean} true when nothing on the details step is missing
 */
export function isEventDetailsComplete(agendaEvent, htmlToText) {
  const summary = agendaEvent && agendaEvent.summary;
  if (!summary || summary.length < 1 || summary.length >= EVENT_TITLE_MAX_LENGTH) {
    return false;
  }
  const owner = agendaEvent.calendar && agendaEvent.calendar.owner;
  if (!owner || !(owner.id || owner.remoteId && owner.providerId)) {
    return false;
  }
  const description = agendaEvent.description || '';
  return htmlToText(description).length <= EVENT_DESCRIPTION_MAX_LENGTH;
}

export function getUserTimezone() {
  const timeZoneOffset = - (new Date().getTimezoneOffset());
  let timezoneHours = Math.abs(parseInt(timeZoneOffset / 60));
  let timezoneMinutes = Math.abs(parseInt(timeZoneOffset % 60));
  timezoneHours = timezoneHours < 10 ? `0${timezoneHours}` : timezoneHours;
  timezoneMinutes = timezoneMinutes < 10 ? `0${timezoneMinutes}` : timezoneMinutes;
  const timezoneSign = timeZoneOffset >= 0 ? '+' : '-';
  return `${timezoneSign}${timezoneHours}:${timezoneMinutes}`;
}

export function getTimeZones() {
  if (TIMEZONES.length) {
    return TIMEZONES;
  }
  const dateObj = new Date(0);
  TIMEZONE_IDS.forEach((timeZone) => {
    const dateFormat = new Intl.DateTimeFormat(eXo.env.portal.language, {
      timeZoneName: 'long',
      second: 'numeric',
      timeZone: timeZone,
    });
    const timeZoneName = dateFormat.format(dateObj);
    TIMEZONES.push({
      value: timeZone,
      text: `${timeZoneName.charAt(2).toUpperCase() + timeZoneName.substring(3, timeZoneName.length)  } (${timeZone})`,
    });
  });
  return TIMEZONES;
}

export function getTimeZoneNameFromTimeZoneId(timeZone) {
  const dateObj = new Date(0);
  const dateFormat = new Intl.DateTimeFormat(eXo.env.portal.language, {
    timeZoneName: 'long',
    second: 'numeric',
    timeZone: timeZone,
  });
  const timeZoneName = dateFormat.format(dateObj);
  const result = `${timeZoneName.charAt(2).toUpperCase() + timeZoneName.substring(3, timeZoneName.length)  } (${timeZone})`;
  return result;
}

export function convertVuetifyRangeToPeriod(range) {
  const rangeStartHour = range.start.hour < 10 ? `0${range.start.hour}` : range.start.hour;
  const rangeStartMinute = range.start.minute < 10 ? `0${range.start.minute}` : range.start.minute;
  const rangeEndHour = range.end.hour < 10 ? `0${range.end.hour}` : range.end.hour;
  const rangeEndMinute = range.end.minute < 10 ? `0${range.end.minute}` : range.end.minute;
  return {
    start: `${range.start.date}T${rangeStartHour}:${rangeStartMinute}:00.000`,
    end: `${range.end.date}T${rangeEndHour}:${rangeEndMinute}:00.000`,
  };
}

export function getWeekNumber(date) {
  const dayNumber = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - dayNumber + 3);
  const firstThursday = date.valueOf();
  date.setMonth(0, 1);
  if (date.getDay() !== 4) {
    date.setMonth(0, 1 + (4 - date.getDay() + 7) % 7);
  }
  return Math.ceil((firstThursday - date.valueOf()) / 604800000) + 1;
}

export function toVuetifyDate(date) {
  return `${date.getFullYear()}-${ 
    pad(date.getMonth() + 1)}-${ 
    pad(date.getDate())} ${
    pad(date.getHours())}:${ 
    pad(date.getMinutes())}`;
}

export function toRFC3339(date, ignoreTime, useTimeZone) {
  if (!date) {
    return null;
  }
  if (typeof date === 'number') {
    date = new Date(date);
  } else if (typeof date === 'string') {
    if (date.indexOf('T') === 10 && date.length > 19) {
      date = date.substring(0, 19);
    }
    date = new Date(date);
  }
  let formattedDate;
  if (ignoreTime) {
    formattedDate = `${date.getFullYear()  }-${
      pad(date.getMonth() + 1)  }-${ 
      pad(date.getDate())  }T00:00:00`;
  } else {
    formattedDate = `${date.getFullYear()  }-${
      pad(date.getMonth() + 1)  }-${ 
      pad(date.getDate())  }T${ 
      pad(date.getHours())  }:${ 
      pad(date.getMinutes())  }:${ 
      pad(date.getSeconds())  
    }`;
  }
  if (useTimeZone) {
    return `${formattedDate}${getUserTimezone()}`;
  }
  return formattedDate;
}

export function toDate(date) {
  if (!date) {
    return null;
  } else if (typeof date === 'number') {
    return new Date(date);
  } else if (typeof date === 'string') {
    if (date.indexOf('T') === 10 && date.length > 19) {
      // Delete TimeZone information
      return new Date(date.substring(0, 19));
    } else if (date.length === 10) {
      // Ensure that TimeZone information doesn't alter the real day of the event
      return new Date(`${date} 00:00:00`);
    }
    return new Date(date);
  } else if (typeof date === 'object') {
    return new Date(date);
  }
}
export function convertDates(event) {
  event.startDate = event.start && toDate(event.start) || null;
  event.endDate = event.end && toDate(event.end) || null;
  return event;
}

export function generateCalendarTitle(calendarType, startDate, periodTitle, weekTitle) {
  if (calendarType === 'week') {
    const weekNumber = getWeekNumber(startDate);
    return `${periodTitle} - ${weekTitle} ${weekNumber}`;
  } else if (calendarType === 'day') {
    const currentDay = startDate.getDate();
    return `${periodTitle} - ${currentDay}`;
  } else if (calendarType === 'month') {
    return periodTitle;
  }
}

export function getDayNameFromDate(date, lang) {
  const options = { weekday: 'long' };
  let d = null;
  if (date) {
    d = toDate(date);
  } else {
    d = new Date();
  }
  return d.toLocaleDateString(lang || 'en', options);
}

const DAYS_ABBREVIATIONS = ['SU', 'MO','TU','WE','TH','FR', 'SA'];

export function getDayNameFromDayAbbreviation(day, lang) {
  const date = new Date(1584226800000);
  const dayNum = DAYS_ABBREVIATIONS.indexOf(day);
  date.setDate(dayNum + 1);
  return date.toLocaleDateString(lang || 'en', {weekday: 'long'});
}

export function getDayNumberFromDate(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.getDay();
}

export function getMonthFromDate(date, lang) {
  const options = { month: 'long' };
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.toLocaleDateString(lang || 'en', options);
}

export function getMonthNameFromMonthNumber(monthNumber, lang) {
  const options = { month: 'long' };
  const d = new Date();
  d.setMonth(monthNumber);
  return d.toLocaleDateString(lang || 'en', options);
}

export function getMonthNumberFromDate(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  return d.getMonth() + 1;
}

export function getDayOfYear(date) {
  let d = null;
  if (date) {
    d = new Date(date);
  } else {
    d = new Date();
  }
  const timestmp = new Date().setFullYear(new Date().getFullYear(), 0, 1);
  const yearFirstDay = Math.floor(timestmp / 86400000);
  const today = Math.ceil(d.getTime()/ 86400000);
  const dayOfYear = today - yearFirstDay;
  return dayOfYear;
}

export function pad(n) {
  return n < 10 && `0${n}` || n;
}

export function getSameTime(dateToChange, dateWithTime) {
  const returnDate = typeof dateToChange === 'string';
  if (typeof dateWithTime === 'string') {
    dateWithTime = new Date(dateWithTime);
  }
  if (typeof dateToChange === 'string') {
    dateToChange = new Date(dateToChange);
  }
  dateToChange.setHours(dateWithTime.getHours());
  dateToChange.setMinutes(dateWithTime.getMinutes());
  dateToChange.setSeconds(dateWithTime.getSeconds());
  return returnDate && dateToChange || toRFC3339(dateToChange);
}

export function areDatesOnSameDay(firstDate, secondDate) {
  if (firstDate === secondDate) {
    return true;
  }
  if (!firstDate || !secondDate) {
    return true;
  }
  if (!(typeof firstDate === 'object')) {
    firstDate = new Date(firstDate);
  }
  if (!(typeof secondDate === 'object')) {
    secondDate = new Date(secondDate);
  }
  return firstDate.getFullYear() === secondDate.getFullYear() &&
  firstDate.getMonth() === secondDate.getMonth() &&
  firstDate.getDate() === secondDate.getDate();
}

export function getWeekSequenceFromDay(settings, calendarType, allDays) {
  let workedDays = [1, 2, 3, 4, 5, 6, 0];
  switch (settings.agendaWeekStartOn) {
  case 'MO':
    workedDays = [1, 2, 3, 4, 5, 6, 0];
    break;
  case 'TU':
    workedDays = [2, 3, 4, 5, 6, 0, 1];
    break;
  case 'WE':
    workedDays = [3, 4, 5, 6, 0, 1, 2];
    break;
  case 'TH':
    workedDays = [4, 5, 6, 0, 1, 2, 3,];
    break;
  case 'FR':
    workedDays = [5, 6, 0, 1, 2, 3, 4];
    break;
  case 'SA':
    workedDays = [6, 0, 1, 2, 3, 4, 5];
    break;
  case 'SU':
    workedDays = [0, 1, 2, 3, 4, 5, 6];
    break;
  default:
    workedDays = [1, 2, 3, 4, 5, 6, 0];
  }
  return calendarType && calendarType === 'week' && settings.showWorkingTime && settings.workedDaysNumber && !allDays? workedDays.slice(0, settings.workedDaysNumber) : workedDays;
}

export function areSameObjects(object1, object2) {
  const keys1 = Object.keys(object1);
  const keys2 = Object.keys(object2);
  if (keys1.length !== keys2.length) {
    return false;
  }
  for (const key of keys1) {
    const val1 = object1[key] && typeof object1[key] === 'object' ? JSON.stringify(object1[key]) : object1[key];
    const val2 = object2[key] && typeof object2[key] === 'object' ? JSON.stringify(object2[key]) : object2[key];
    if (val1 !== val2) {
      return false;
    }
  }
  return true;
}

export function isShortEvent(event) {
  return Math.floor(toDate(event.endDate).getTime() / 60000) - Math.floor(toDate(event.startDate).getTime() / 60000) < 60;
}

/*
 * The colour three connectors write on every event they return: Google,
 * Office 365 and Exchange each set `event.color = '#FFFFFF'` unconditionally,
 * their providers giving them no calendar colour to pass on. It is a
 * placeholder rather than a colour — painted, it is invisible on a light
 * ground and wrong on a dark one — so it counts as no colour at all.
 *
 * Nothing else can produce it: eXo's own swatches hold no white, and the
 * CalDAV palette walks a derived colour down until it clears WCAG AA against
 * white, which white itself never does.
 */
const PLACEHOLDER_EVENT_COLOR = '#FFFFFF';

/**
 * The colour an event is recognised by: its calendar's, else the one set on
 * the event itself.
 *
 * <p>
 * The same chain, in the same order, that the calendar grid paints its events
 * with (`AgendaCalendar.getEventBorderColor`), so one event is not one colour
 * in the grid and another in a list beside it. What is deliberately left out
 * is that method's `#2196F3` fallback: a surface that draws a colour to say
 * WHICH calendar a row came from must draw nothing when it does not know,
 * rather than a default that reads as an answer.
 *
 * <p>
 * Works for either source: an event of eXo's own calendars carries the
 * calendar the REST layer attached, and one read live from a CalDAV account
 * carries the colour that account's palette gave its collection.
 *
 * @param {Object} event an event from either source
 * @returns {String} the hex colour, empty when the event has none to show
 */
export function calendarColor(event) {
  const color = event && (event.calendar && event.calendar.color || event.color) || '';
  return color.toUpperCase() === PLACEHOLDER_EVENT_COLOR ? '' : color;
}

export function addOpacity(hexColor, opacity) {
  hexColor = hexColor.replace('#', '');
  const duplicate = hexColor.length < 6;
  const rHex = duplicate && `${hexColor[0]}${hexColor[0]}` || `${hexColor[0]}${hexColor[1]}`;
  const gHex = duplicate && `${hexColor[1]}${hexColor[1]}` || `${hexColor[2]}${hexColor[3]}`;
  const bHex = duplicate && `${hexColor[2]}${hexColor[2]}` || `${hexColor[4]}${hexColor[5]}`;
  const r = parseInt(rHex, 16);
  const g = parseInt(gHex, 16);
  const b = parseInt(bHex, 16);
  return `rgba(${r},${g},${b},${opacity / 100})`;
}

export function roundTime(time, down = true) {
  return down
    ? time - time % MINIMUM_TIME_INTERVAL_MS
    : time + (MINIMUM_TIME_INTERVAL_MS - time % MINIMUM_TIME_INTERVAL_MS);
}

export function toDateTime(tms, down = true) {
  return roundTime(new Date(tms.year, tms.month - 1, tms.day, tms.hour, tms.minute).getTime(), down);
}

/**
 * Merges the remote events several connected accounts returned into one
 * array, in a deterministic order, deduplicating the events two accounts
 * both hold.
 *
 * Every event is tagged with the connector it came from — with several
 * accounts connected, "the connected connector" stops naming one thing, and
 * a remote event's avatar or push target must be the account the event
 * actually belongs to.
 *
 * The dedup key is the event's ICS UID together with its start instant: the
 * UID alone would collapse the occurrences of a recurring series (which
 * share it), while UID + start only matches the same occurrence of the same
 * event surfaced by two accounts. When two accounts do return the same
 * occurrence, the account whose connector declares the lower rank wins,
 * name order breaking ties — a rule rather than whichever response landed
 * last, so the grid attributes the event to the same account on every load.
 *
 * @param {Array} eventsByConnector one entry per account,
 *          `{connector, events}`
 * @returns {Array} the merged events, each carrying its `connector`
 */
export function mergeRemoteEvents(eventsByConnector) {
  const orderedResults = (eventsByConnector || []).slice()
    .sort((result1, result2) => {
      const rank1 = result1.connector && Number.isFinite(result1.connector.rank) ? result1.connector.rank : Number.MAX_SAFE_INTEGER;
      const rank2 = result2.connector && Number.isFinite(result2.connector.rank) ? result2.connector.rank : Number.MAX_SAFE_INTEGER;
      return rank1 - rank2
        || String(result1.connector && result1.connector.name || '').localeCompare(String(result2.connector && result2.connector.name || ''));
    });
  const seenOccurrences = new Set();
  const mergedEvents = [];
  orderedResults.forEach(result => (result.events || []).forEach(event => {
    event.connector = result.connector;
    const startTime = event.startDate && toDate(event.startDate).getTime()
      || event.start && toDate(event.start).getTime()
      || '';
    const occurrenceKey = event.id && `${event.id}|${startTime}` || null;
    if (occurrenceKey && seenOccurrences.has(occurrenceKey)) {
      return;
    }
    if (occurrenceKey) {
      seenOccurrences.add(occurrenceKey);
    }
    mergedEvents.push(event);
  }));
  return mergedEvents;
}

/**
 * Splits what the connected accounts answered into the events a view may
 * draw and the accounts that could not answer at all.
 *
 * <p>
 * An account that failed reaches here as `{connector, failed: true}` and
 * carries no events array, which is the whole point: `{connector, events: []}`
 * would say the account answered "nothing", and no view downstream could
 * then tell "you have nothing scheduled there" from "we could not ask".
 *
 * <p>
 * "Answered" and "failed" are two axes here, not one. A connector able to
 * report a partial read — most of its calendars answered, one did not —
 * arrives as `{connector, events, failed: true}`, and both halves of that are
 * true at once: the events it did return belong on the grid, AND the view has
 * to own up to the ones it could not read. Partitioning on `failed` alone
 * would silently throw the returned events away to report the missing ones,
 * which is a worse week than the failure caused. So the events of every result
 * that carries an events array are merged, and every result that carries the
 * flag names its connector — a result may do both.
 *
 * @param {Array} resultsByConnector one entry per account: `{connector,
 *          events}`, `{connector, failed: true}`, or `{connector, events,
 *          failed: true}` for a partial read
 * @returns {Object} `{events, failedConnectors}` — the merged events of
 *          everything that was read, and the connectors that lost something
 */
export function splitRemoteEventResults(resultsByConnector) {
  const results = resultsByConnector || [];
  return {
    events: mergeRemoteEvents(results.filter(result => result.events)),
    failedConnectors: results.filter(result => result.failed)
      .map(result => result.connector)
      .filter(connector => !!connector),
  };
}

/**
 * Normalises what a connector's `getEvents` resolved with.
 *
 * <p>
 * Two shapes are accepted, on purpose. A plain array is what every connector
 * answers that only knows how to succeed or throw — Google, Office 365,
 * Exchange — and it keeps working untouched. `{events, failed}` is what a
 * connector answers when its own server can fail in a way the HTTP layer
 * hides: the CalDAV add-on reads a third-party calendar server, and that
 * server being down reaches the platform as a perfectly successful 200 with
 * nothing in it. Only the connector can tell the two apart, so only the
 * connector can say so, and this is where a view hears it.
 *
 * @param {Array|Object} answer what the connector resolved with
 * @returns {Object} `{events, failed}`, both always present
 */
export function readConnectorAnswer(answer) {
  if (Array.isArray(answer)) {
    return {events: answer, failed: false};
  }
  const payload = answer || {};
  return {events: payload.events || [], failed: !!payload.failed};
}

/**
 * Names the accounts a view has to own up to not having read, preferring the
 * address the account is signed in as over the connector's technical name: a
 * user recognises "jane@example.com", not "caldav".
 *
 * @param {Array} failedConnectors the connectors whose read failed
 * @returns {Array} one display name per account, empty names dropped
 */
export function failedSourceNames(failedConnectors) {
  return (failedConnectors || [])
    .map(connector => connector && (connector.user || connector.name) || '')
    .filter(name => !!name);
}

/**
 * The three things that can have happened when one participant's busy time
 * was asked for. The server spells them exactly this way, and so does this
 * module: one idea, one spelling, across the wire and the views.
 */
export const BUSY_TIME_DISCLOSED = 'disclosed';

/**
 * The participant does not disclose their busy time to the asking user — a
 * choice of theirs, not an incident.
 */
export const BUSY_TIME_NOT_DISCLOSED = 'not_disclosed';

/**
 * The read broke. Nothing is known, and it is not the participant's doing.
 */
export const BUSY_TIME_FAILED = 'failed';

/**
 * How one participant is identified on a screen that has not saved its event
 * yet.
 *
 * <p>
 * <strong>Not the identity id.</strong> A participant added through the
 * suggester carries only `{providerId, remoteId, profile}` —
 * `convertSuggesterItemToIdentity` in social builds exactly that — and gains
 * an `id` only once the event is saved and the server resolves them. Keying
 * a screen off `identity.id` therefore drops every participant of a NEW
 * event, which is the whole population this feature exists for.
 *
 * <p>
 * `providerId:remoteId` is what the attendees drawer already keys its own
 * duplicate check on, and it is present on both attendee shapes: the one the
 * suggester builds and the one AgendaEventFormAttendees builds for the
 * organiser. It is also the only key a participant whose identity could NOT
 * be resolved still has — and that participant has to be nameable, or the
 * screen goes quiet about them again.
 *
 * @param {Object} attendee an event attendee
 * @returns {String} the participant key, empty when the attendee carries
 *          neither a provider/remote pair nor an id
 */
export function participantKey(attendee) {
  const identity = attendee && attendee.identity;
  if (!identity) {
    return '';
  }
  if (identity.providerId && identity.remoteId) {
    return `${identity.providerId}:${identity.remoteId}`;
  }
  return identity.id && String(identity.id) || '';
}

/**
 * Splits what the availability endpoint answered into the blocks a view may
 * draw and the people it has to own up to not having checked.
 *
 * <p>
 * This is `splitRemoteEventResults` for people rather than accounts, and it
 * exists for the same reason: the events a view can draw and the sources it
 * could not read are two different things, and a view that keeps only the
 * first paints a slot free that is not.
 *
 * <p>
 * The one difference is that a person carries THREE outcomes where an account
 * carries two. `{disclosure: 'disclosed', busy: []}` is a real answer — that
 * person has nothing on — while `not_disclosed` and `failed` are two ways of
 * having no answer at all, and they are kept apart because they read
 * differently to an organiser: one is a colleague's decision to keep their
 * calendar to themselves, the other is a breakage that may be gone in a
 * minute. Collapsing any two of the three is what turns an empty grid into a
 * booking over someone's real meeting.
 *
 * <p>
 * A record whose `disclosure` this function does not recognise counts as
 * unchecked, not as checked. An unknown status is not an answer.
 *
 * @param {Array} records one per participant, `{identityId, disclosure,
 *          busy}`, as the endpoint answered
 * @returns {Object} `{busyByIdentityId, checkedIds, notDisclosedIds,
 *          failedIds}` — the blocks per participant, and the three sets of
 *          identifiers, which partition everything asked about
 */
export function splitBusyTimeResults(records) {
  const busyByIdentityId = {};
  const checkedIds = [];
  const notDisclosedIds = [];
  const failedIds = [];
  (records || []).forEach(record => {
    // A record with no identifier names nobody, so it can be neither drawn
    // nor owned up to; == null catches both a missing and a null identifier.
    if (!record || record.identityId == null) {
      return;
    }
    const identityId = String(record.identityId);
    if (record.disclosure === BUSY_TIME_DISCLOSED) {
      busyByIdentityId[identityId] = record.busy || [];
      checkedIds.push(identityId);
    } else if (record.disclosure === BUSY_TIME_FAILED) {
      failedIds.push(identityId);
    } else {
      notDisclosedIds.push(identityId);
    }
  });
  return {busyByIdentityId, checkedIds, notDisclosedIds, failedIds};
}

/**
 * The colour every participant busy block is drawn in.
 *
 * <p>
 * One neutral grey for everybody, deliberately. Every other colour on this
 * grid means "this calendar" — the organiser's own calendars and the accounts
 * they connected — and a participant's block is not one of those; it is
 * background the slot is picked against. Who a block belongs to is said by the
 * avatar on it, which is a fact, rather than by a colour, which would be a
 * legend nobody was given.
 */
export const PARTICIPANT_BUSY_COLOR = '#9e9e9e';

/**
 * Turns one participant's busy ranges into the pseudo-events a calendar grid
 * draws behind the slots being picked.
 *
 * <p>
 * The blocks carry nothing but the person and the two instants. There is no
 * title to show because the server never sent one, which is the whole reason
 * showing someone else's calendar here is defensible at all — so the label the
 * grid paints is the word the caller passes, the same word for everybody.
 *
 * @param {Array} blocks the ranges the endpoint answered, `{start, end}`
 * @param {Object} attendee the participant these ranges belong to
 * @param {String} label what every block says, e.g. "Busy"
 * @returns {Array} one grid event per range
 */
export function toParticipantBusyEvents(blocks, attendee, label) {
  return (blocks || [])
    .filter(block => block && block.start && block.end)
    .map(block => convertDates({
      type: 'participantBusy',
      color: PARTICIPANT_BUSY_COLOR,
      identityId: attendee && attendee.identity && attendee.identity.id,
      identity: attendee && attendee.identity,
      summary: label,
      allDay: false,
      start: block.start,
      end: block.end,
    }));
}
